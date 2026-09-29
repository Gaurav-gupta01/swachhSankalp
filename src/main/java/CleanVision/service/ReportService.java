package CleanVision.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import CleanVision.ai.AiResult;
import CleanVision.domain.AnimalCondition;
import CleanVision.domain.AnimalType;
import CleanVision.domain.Assignment;
import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.StatusHistory;
import CleanVision.domain.Team;
import CleanVision.domain.TeamType;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.AnimalReportForm;
import CleanVision.dto.Forms.ReportForm;
import CleanVision.repository.AssignmentRepository;
import CleanVision.repository.ReportRepository;
import CleanVision.repository.StatusHistoryRepository;
import CleanVision.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    private final ReportRepository reports;
    private final StatusHistoryRepository history;
    private final AssignmentRepository assignments;
    private final ImageStorageService images;
    private final TeamRepository teams;

    @Transactional
    public Report create(User reporter, ReportForm form, AiResult ai) {
        if (!images.exists(form.getImageFile())) {
            throw new AppException("The uploaded image was not found, please upload it again");
        }

        Report report = new Report();
        report.setReporter(reporter);
        report.setImageUrl(form.getImageFile());
        report.setWasteType(ai.category());
        report.setConfidence(ai.confidence());
        report.setSeverity(ai.severity());
        report.setLatitude(form.getLatitude());
        report.setLongitude(form.getLongitude());
        report.setAddress(form.getAddress());
        report.setWard(form.getWard());
        report.setDescription(form.getDescription());
        report.setStatus(ReportStatus.SUBMITTED);
        report.setCreatedAt(LocalDateTime.now());
        report = reports.save(report);

        report.setPublicId("CV" + (10000 + report.getId()));
        report.setStatus(ReportStatus.AI_ANALYZED);
        report = reports.save(report);

        record(report, ReportStatus.SUBMITTED, reporter.getName(), "Report submitted by citizen");
        record(report, ReportStatus.AI_ANALYZED, "Swachh Sankalp AI",
                ai.category().getLabel() + " at " + Math.round(ai.confidence() * 100) + "% confidence - " + ai.reason());
        return report;
    }

    /**
     * Files an animal report. These skip the image classifier entirely — the citizen
     * states the animal and its condition — but they run through the same queue,
     * dashboards and cleanup workflow as every other report.
     */
    @Transactional
    public Report createAnimal(User reporter, AnimalReportForm form, String imageFilename) {
        if (imageFilename != null && !images.exists(imageFilename)) {
            throw new AppException("The uploaded image was not found, please upload it again");
        }

        AnimalType animalType = AnimalType.find(form.getAnimalType())
                .orElseThrow(() -> new AppException("Choose one of the listed animals"));
        AnimalCondition animalCondition = AnimalCondition.find(form.getAnimalCondition())
                .orElseThrow(() -> new AppException("Choose one of the listed animal conditions"));

        Report report = new Report();
        report.setReporter(reporter);
        report.setImageUrl(imageFilename);
        report.setWasteType(WasteCategory.ANIMAL);
        report.setAnimalType(animalType);
        report.setAnimalCondition(animalCondition);
        report.setConfidence(0.0);
        report.setSeverity(animalCondition.getSeverity());
        report.setLatitude(form.getLatitude());
        report.setLongitude(form.getLongitude());
        report.setAddress(form.getAddress());
        report.setWard(form.getWard());
        report.setDescription(form.getDescription());
        report.setStatus(ReportStatus.SUBMITTED);
        report.setCreatedAt(LocalDateTime.now());
        report = reports.save(report);

        report.setPublicId("CV" + (10000 + report.getId()));
        report = reports.save(report);

        record(report, ReportStatus.SUBMITTED, reporter.getName(),
                animalType.getIcon() + " " + animalType.getLabel() + " · "
                        + animalCondition.getIcon() + " " + animalCondition.getLabel()
                        + " — reported by citizen");

        autoAssignToAnimalTeam(report, reporter.getName());
        return report;
    }

    /**
     * Animal reports go straight to an animal rescue crew — prefer one covering the
     * report's ward, otherwise fall back to any active animal team. If the city has
     * none configured yet the report stays in the queue for an officer to assign.
     */
    private void autoAssignToAnimalTeam(Report report, String updatedBy) {
        List<Team> candidates = List.of();
        if (report.getWard() != null && !report.getWard().isBlank()) {
            candidates = teams.findByWardAndActiveTrueAndTeamType(report.getWard().trim(), TeamType.ANIMAL);
        }
        if (candidates.isEmpty()) {
            candidates = teams.findByActiveTrueAndTeamTypeOrderByTeamNameAsc(TeamType.ANIMAL);
        }
        if (candidates.isEmpty()) {
            log.warn("No active animal rescue team configured — {} left for manual assignment",
                    report.getPublicId());
            return;
        }
        assign(report, candidates.get(0), updatedBy);
    }

    /** Waste crews handle waste, animal rescue crews handle animals — never both. */
    private void requireMatchingTeam(Report report, Team team) {
        if (team == null) {
            throw new AppException("Choose a cleanup team first");
        }
        TeamType teamType = team.getTeamType() == null ? TeamType.WASTE : team.getTeamType();
        TeamType required = report.getWasteType() == WasteCategory.ANIMAL
                ? TeamType.ANIMAL
                : TeamType.WASTE;
        if (teamType != required) {
            String article = teamType == TeamType.ANIMAL ? "an " : "a ";
            throw new AppException(team.getTeamName() + " is " + article
                    + teamType.getLabel().toLowerCase()
                    + " team and cannot take " + report.getPublicId() + " ("
                    + required.getLabel().toLowerCase() + " reports only)");
        }
    }

    @Transactional(readOnly = true)
    public Report byPublicId(String publicId) {
        return reports.findByPublicId(publicId)
                .orElseThrow(() -> new AppException("Report " + publicId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Report> all() {
        return reports.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<Report> byUser(User user) {
        return reports.findByReporterIdOrderByCreatedAtDesc(user.getId());
    }

    @Transactional
    public List<Report> byTeam(CleanVision.domain.Team team) {
        return assignments.findByTeamIdAndActiveTrue(team.getId()).stream()
                .map(Assignment::getReport)
                .sorted(java.util.Comparator.comparing(Report::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public Report changeStatus(Report report, ReportStatus target, String updatedBy, String note) {
        if (report.getStatus() == target) {
            return report;
        }
        report.setStatus(target);
        if (target == ReportStatus.RESOLVED || target == ReportStatus.CLOSED) {
            report.setResolvedAt(LocalDateTime.now());
        }
        if (target.ordinal() < ReportStatus.RESOLVED.ordinal()) {
            report.setResolvedAt(null);
        }
        reports.save(report);
        record(report, target, updatedBy, note);
        return report;
    }

    @Transactional
    public Report assign(Report report, Team team, String updatedBy) {
        requireMatchingTeam(report, team);

        assignments.findFirstByReportIdAndActiveTrue(report.getId()).ifPresent(existing -> {
            existing.setActive(false);
            existing.setCompletedAt(LocalDateTime.now());
            assignments.save(existing);
        });

        Assignment assignment = new Assignment();
        assignment.setReport(report);
        assignment.setTeam(team);
        assignments.save(assignment);

        if (report.getStatus().ordinal() < ReportStatus.ASSIGNED.ordinal()) {
            changeStatus(report, ReportStatus.ASSIGNED, updatedBy, "Assigned to " + team.getTeamName());
        } else {
            record(report, report.getStatus(), updatedBy, "Re-assigned to " + team.getTeamName());
        }
        return report;
    }

    @Transactional
    public Report markCollected(Report report, Double quantityKg, Double teamLatitude,
            Double teamLongitude, String updatedBy) {
        if (report.getStatus().ordinal() < ReportStatus.IN_PROGRESS.ordinal()) {
            throw new AppException("Start the cleanup before marking " + report.getPublicId()
                    + " as collected");
        }
        if (report.getStatus().ordinal() >= ReportStatus.RESOLVED.ordinal()) {
            throw new AppException(report.getPublicId() + " is already "
                    + report.getStatus().getLabel().toLowerCase());
        }

        report.setQuantityKg(quantityKg);
        report.setTeamLatitude(teamLatitude != null ? teamLatitude : report.getLatitude());
        report.setTeamLongitude(teamLongitude != null ? teamLongitude : report.getLongitude());
        report.setCollectedAt(LocalDateTime.now());
        reports.save(report);

        String quantity = quantityKg == null ? "" : " · ~" + quantityKg + " kg";
        changeStatus(report, ReportStatus.COLLECTED, updatedBy,
                "Waste collected by cleanup team" + quantity);
        return report;
    }

    @Transactional
    public Report confirmDisposal(Report report, CleanVision.domain.DisposalFacility facility,
            Double distanceMeters, Double durationSeconds, String updatedBy) {
        if (facility == null) {
            throw new AppException("Choose a disposal facility first");
        }
        if (report.getStatus().ordinal() < ReportStatus.COLLECTED.ordinal()) {
            throw new AppException("Mark " + report.getPublicId() + " as collected before confirming disposal");
        }

        report.setDisposalFacility(facility);
        report.setDisposedAt(LocalDateTime.now());
        report.setRouteDistanceM(distanceMeters);
        report.setRouteDurationS(durationSeconds);
        reports.save(report);

        changeStatus(report, ReportStatus.RESOLVED, updatedBy,
                "Disposal confirmed at " + facility.getName() + " ("
                        + facility.getFacilityType().getLabel() + ")");
        return report;
    }

    @Transactional
    public Report attachAfterImage(Report report, MultipartFile file, String updatedBy) {
        String filename = images.store(file);
        String previous = report.getAfterImageUrl();
        report.setAfterImageUrl(filename);
        reports.save(report);
        images.delete(previous);

        if (report.getStatus().ordinal() >= ReportStatus.RESOLVED.ordinal()) {
            record(report, report.getStatus(), updatedBy, "After-cleanup photo updated");
        } else {
            record(report, report.getStatus(), updatedBy, "After-cleanup photo uploaded");
        }
        return report;
    }

    @Transactional(readOnly = true)
    public Report latestCompletedWithAfterImage() {
        return reports.findFirstByAfterImageUrlIsNotNullOrderByCreatedAtDesc().orElse(null);
    }

    @Transactional(readOnly = true)
    public Assignment activeAssignment(Report report) {
        return assignments.findFirstByReportIdAndActiveTrue(report.getId()).orElse(null);
    }

    @Transactional(readOnly = true)
    public long activeAssignmentCount(Team team) {
        return assignments.countByTeamIdAndActiveTrue(team.getId());
    }

    @Transactional(readOnly = true)
    public List<StatusHistory> timeline(Report report) {
        return history.findByReportIdOrderByTimestampDesc(report.getId());
    }

    @Transactional
    public void delete(Report report) {
        images.delete(report.getImageUrl());
        images.delete(report.getAfterImageUrl());
        assignments.deleteByReportId(report.getId());
        history.deleteByReportId(report.getId());
        reports.delete(report);
    }

    private void record(Report report, ReportStatus status, String updatedBy, String note) {
        StatusHistory entry = new StatusHistory();
        entry.setReport(report);
        entry.setStatus(status);
        entry.setUpdatedBy(updatedBy);
        entry.setNote(note);
        entry.setTimestamp(LocalDateTime.now());
        history.save(entry);
    }
}
