package CleanVision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.Assignment;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    Optional<Assignment> findFirstByReportIdAndActiveTrue(Long reportId);

    List<Assignment> findByActiveTrue();

    long countByTeamIdAndActiveTrue(Long teamId);

    void deleteByReportId(Long reportId);
}
