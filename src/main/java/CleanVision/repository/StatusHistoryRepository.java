package CleanVision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.StatusHistory;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    List<StatusHistory> findByReportIdOrderByTimestampDesc(Long reportId);

    void deleteByReportId(Long reportId);
}
