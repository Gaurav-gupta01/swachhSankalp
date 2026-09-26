package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Severity;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.DashboardStatistics;
import CleanVision.repository.ReportRepository;

class StatsServiceTest {

    @Test
    void newRegistrationsSeeAnEmptyDashboardWhileOfficersSeeGlobalData() {
        User alice = new User();
        alice.setId(1L);
        User bob = new User();
        bob.setId(2L);

        Report aliceReport = new Report();
        aliceReport.setReporter(alice);
        aliceReport.setWasteType(WasteCategory.PLASTIC);
        aliceReport.setSeverity(Severity.MEDIUM);
        aliceReport.setStatus(ReportStatus.AI_ANALYZED);
        aliceReport.setConfidence(0.91);
        aliceReport.setCreatedAt(LocalDateTime.now());

        ReportRepository repository = Mockito.mock(ReportRepository.class);
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(aliceReport));
        when(repository.findByReporterIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(aliceReport));
        when(repository.findByReporterIdOrderByCreatedAtDesc(2L)).thenReturn(List.of());

        StatsService stats = new StatsService(repository);

        DashboardStatistics freshUser = stats.forUser(bob);
        assertThat(freshUser.totalReports()).isZero();
        assertThat(freshUser.recentReports()).isEmpty();
        assertThat(freshUser.byCategory()).isEmpty();
        assertThat(freshUser.hotspots()).isEmpty();

        DashboardStatistics existingUser = stats.forUser(alice);
        assertThat(existingUser.totalReports()).isEqualTo(1);
        assertThat(existingUser.recentReports()).hasSize(1);

        assertThat(stats.dashboard().totalReports()).isEqualTo(1);
    }
}
