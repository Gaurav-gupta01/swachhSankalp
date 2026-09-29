package CleanVision.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.DashboardStatistics;
import CleanVision.repository.ReportRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("dd MMM");

    private final ReportRepository reports;

    @Transactional(readOnly = true)
    public DashboardStatistics dashboard() {
        return build(reports.findAllByOrderByCreatedAtDesc());
    }

    @Transactional(readOnly = true)
    public DashboardStatistics forUser(CleanVision.domain.User user) {
        return build(reports.findByReporterIdOrderByCreatedAtDesc(user.getId()));
    }

    private DashboardStatistics build(List<Report> all) {
        long pending = all.stream().filter(r -> r.getStatus().ordinal() < ReportStatus.ASSIGNED.ordinal()).count();
        long inProgress = all.stream()
                .filter(r -> r.getStatus() == ReportStatus.ASSIGNED
                        || r.getStatus() == ReportStatus.IN_PROGRESS
                        || r.getStatus() == ReportStatus.COLLECTED)
                .count();
        long resolved = all.stream()
                .filter(r -> r.getStatus() == ReportStatus.RESOLVED || r.getStatus() == ReportStatus.CLOSED)
                .count();
        long disposalPending = all.stream()
                .filter(r -> r.getDisposedAt() == null && r.getCollectedAt() != null)
                .count();

        double averageConfidence = all.isEmpty() ? 0
                : all.stream()
                        .filter(report -> report.getWasteType() != WasteCategory.ANIMAL)
                        .mapToDouble(Report::getConfidence).average().orElse(0);

        Map<String, Long> byCategory = new LinkedHashMap<>();
        for (WasteCategory category : WasteCategory.values()) {
            byCategory.put(category.getLabel(), all.stream()
                    .filter(r -> r.getWasteType() == category).count());
        }
        byCategory.values().removeIf(count -> count == 0);

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (Severity severity : Severity.values()) {
            bySeverity.put(severity.getLabel(),
                    all.stream().filter(r -> r.getSeverity() == severity).count());
        }

        Map<String, Long> wardCounts = new LinkedHashMap<>();
        for (Report report : all) {
            if (report.getWard() != null && !report.getWard().isBlank()) {
                wardCounts.merge(report.getWard(), 1L, Long::sum);
            }
        }
        Map<String, Long> byWard = new LinkedHashMap<>();
        wardCounts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .forEach(entry -> byWard.put(entry.getKey(), entry.getValue()));

        List<Map<String, Object>> recent = all.stream().limit(6).map(this::summary).toList();
        List<Map<String, Object>> hotspots = hotspots(all);
        List<Map<String, Object>> overTime = overTime(all);

        return new DashboardStatistics(
                all.size(),
                pending,
                inProgress,
                resolved,
                disposalPending,
                Math.round(averageConfidence * 1000) / 10.0,
                byCategory,
                bySeverity,
                byWard,
                recent,
                hotspots,
                overTime);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> allReportsGeo() {
        return reports.findByLatitudeIsNotNullAndLongitudeIsNotNull().stream().map(report -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("publicId", report.getPublicId());
            point.put("wasteType", report.getWasteType().getLabel());
            point.put("severity", report.getSeverity().name());
            point.put("severityLabel", report.getSeverity().getLabel());
            point.put("status", report.getStatus().getLabel());
            point.put("latitude", report.getLatitude());
            point.put("longitude", report.getLongitude());
            point.put("ward", report.getWard() == null ? "" : report.getWard());
            point.put("address", report.getAddress() == null ? "" : report.getAddress());
            return point;
        }).toList();
    }

    private List<Map<String, Object>> hotspots(List<Report> all) {
        Map<String, List<Report>> byWard = new LinkedHashMap<>();
        for (Report report : all) {
            if (report.getLatitude() == null || report.getLongitude() == null) {
                continue;
            }
            String ward = report.getWard() == null || report.getWard().isBlank()
                    ? "Unassigned"
                    : report.getWard();
            byWard.computeIfAbsent(ward, key -> new ArrayList<>()).add(report);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        byWard.forEach((ward, group) -> {
            double lat = group.stream().mapToDouble(Report::getLatitude).average().orElse(0);
            double lng = group.stream().mapToDouble(Report::getLongitude).average().orElse(0);
            long count = group.size();

            Map<String, Object> spot = new LinkedHashMap<>();
            spot.put("ward", ward);
            spot.put("count", count);
            spot.put("latitude", Math.round(lat * 10000.0) / 10000.0);
            spot.put("longitude", Math.round(lng * 10000.0) / 10000.0);
            spot.put("level", count >= 4 ? "critical" : count >= 2 ? "watch" : "normal");
            result.add(spot);
        });
        result.sort((a, b) -> Long.compare((Long) b.get("count"), (Long) a.get("count")));
        return result;
    }

    private List<Map<String, Object>> overTime(List<Report> all) {
        LocalDate today = LocalDate.now();
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (int offset = 6; offset >= 0; offset--) {
            byDay.put(today.minusDays(offset), 0L);
        }
        for (Report report : all) {
            if (report.getCreatedAt() == null) {
                continue;
            }
            LocalDate day = report.getCreatedAt().toLocalDate();
            if (byDay.containsKey(day)) {
                byDay.put(day, byDay.get(day) + 1);
            }
        }

        List<Map<String, Object>> series = new ArrayList<>();
        byDay.forEach((day, count) -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("day", day.format(DAY_FORMAT));
            point.put("count", count);
            series.add(point);
        });
        return series;
    }

    private Map<String, Object> summary(Report report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("publicId", report.getPublicId());
        map.put("wasteType", report.getWasteType().getLabel());
        map.put("icon", report.getWasteType().getIcon());
        map.put("severity", report.getSeverity().getLabel());
        map.put("status", report.getStatus().getLabel());
        map.put("statusCode", report.getStatus().name());
        map.put("severityCode", report.getSeverity().name());
        map.put("ward", report.getWard() == null ? "-" : report.getWard());
        map.put("address", report.getAddress() == null ? "-" : report.getAddress());
        map.put("imageUrl", report.getImageUrl());
        map.put("createdAt", report.getCreatedAt());
        map.put("disposal", disposalLabel(report));
        map.put("disposalCode", disposalCode(report));
        map.put("facilityType", report.getDisposalFacility() == null ? ""
                : report.getDisposalFacility().getFacilityType().getLabel());
        map.put("quantityKg", report.getQuantityKg());
        return map;
    }

    private String disposalLabel(Report report) {
        if (report.getDisposalFacility() != null) {
            return report.getDisposalFacility().getName();
        }
        return report.getCollectedAt() != null ? "Disposal pending" : "—";
    }

    private String disposalCode(Report report) {
        if (report.getDisposalFacility() != null) {
            return "DONE";
        }
        return report.getCollectedAt() != null ? "PENDING" : "NA";
    }
}
