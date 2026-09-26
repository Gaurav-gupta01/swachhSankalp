package CleanVision.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import CleanVision.ai.AiResult;
import CleanVision.domain.Assignment;
import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.StatusHistory;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.dto.Forms.ReportForm;
import CleanVision.repository.AssignmentRepository;
import CleanVision.repository.ReportRepository;
import CleanVision.repository.StatusHistoryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reports;
    private final StatusHistoryRepository history;
    private final AssignmentRepository assignments;
    private final ImageStorageService images;

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
        record(report, ReportStatus.AI_ANALYZED, "CleanVision AI",
                ai.category().getLabel() + " at " + Math.round(ai.confidence() * 100) + "% confidence - " + ai.reason());
        return report;
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
    public Report attachAfterImage(Report report, MultipartFile file, String updatedBy) {
        String filename = images.store(file);
        String previous = report.getAfterImageUrl();
        report.setAfterImageUrl(filename);
        reports.save(report);
        images.delete(previous);

        if (report.getStatus().ordinal() < ReportStatus.RESOLVED.ordinal()) {
            changeStatus(report, ReportStatus.RESOLVED, updatedBy, "After-cleanup photo uploaded");
        } else {
            record(report, report.getStatus(), updatedBy, "After-cleanup photo updated");
        }
        return report;
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
