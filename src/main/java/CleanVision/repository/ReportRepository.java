package CleanVision.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByPublicId(String publicId);

    List<Report> findByReporterIdOrderByCreatedAtDesc(Long userId);

    List<Report> findAllByOrderByCreatedAtDesc();

    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    List<Report> findBySeverityOrderByCreatedAtDesc(Severity severity);

    List<Report> findByWard(String ward);

    long countByStatus(ReportStatus status);

    long countByWasteType(WasteCategory wasteType);

    long countByCreatedAtAfter(LocalDateTime after);

    List<Report> findByLatitudeIsNotNullAndLongitudeIsNotNull();

    List<Report> findByCreatedAtAfter(java.time.LocalDateTime after);
}
