package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Role;
import CleanVision.domain.Team;
import CleanVision.domain.TeamType;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.AnimalReportForm;
import CleanVision.repository.ReportRepository;
import CleanVision.repository.TeamRepository;
import CleanVision.repository.UserRepository;

/**
 * The rescue crew's side of an animal report: it only ever sees animal work, and
 * uploading the after-rescue photo is what closes the report for the citizen.
 */
@SpringBootTest
@Transactional
class AnimalRescueWorkflowTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private UserRepository users;

    @Autowired
    private ReportRepository reports;

    @Autowired
    private CleanVision.repository.AssignmentRepository assignments;

    private User citizen;
    private Team wasteTeam;
    private Team animalTeam;

    @BeforeEach
    void setUp() {
        citizen = users.findByEmailIgnoreCase("citizen@demo.in").orElseGet(this::newCitizen);
        wasteTeam = team("Waste Crew", TeamType.WASTE, "Ward 9");
        animalTeam = team("Rescue Crew", TeamType.ANIMAL, "Ward 9");
        deactivateOtherTeams();
    }

    @Test
    void rescueQueueNeverShowsWasteWork() {
        Report animalReport = reportService.createAnimal(citizen, animalForm(), null);
        Report wasteReport = wasteReport();
        reportService.assign(wasteReport, wasteTeam, "Officer");

        List<Report> rescueQueue = reportService.byTeam(animalTeam);
        List<Report> wasteQueue = reportService.byTeam(wasteTeam);

        assertThat(rescueQueue).extracting(Report::getPublicId)
                .containsExactly(animalReport.getPublicId());
        assertThat(wasteQueue).extracting(Report::getPublicId)
                .containsExactly(wasteReport.getPublicId());
        assertThat(rescueQueue).allSatisfy(r -> assertThat(r.getWasteType()).isEqualTo(WasteCategory.ANIMAL));
    }

    @Test
    void afterRescuePhotoClosesTheReport() {
        Report report = reportService.createAnimal(citizen, animalForm(), null);
        startRescue(report);

        reportService.closeAfterRescue(report, photo("rescue-after.jpg"), "Rescue Crew");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.CLOSED);
        assertThat(report.getAfterImageUrl()).isNotBlank();
        assertThat(report.getResolvedAt()).isNotNull();

        List<String> notes = reportService.timeline(report).stream()
                .map(CleanVision.domain.StatusHistory::getNote)
                .toList();
        assertThat(notes).anySatisfy(note -> assertThat(note)
                .containsIgnoringCase("report closed successfully"));
    }

    @Test
    void citizenSeesTheClosedReport() {
        Report report = reportService.createAnimal(citizen, animalForm(), null);
        startRescue(report);
        reportService.closeAfterRescue(report, photo("closed.jpg"), "Rescue Crew");

        Report reloaded = reportService.byPublicId(report.getPublicId());
        assertThat(reloaded.getStatus()).isEqualTo(ReportStatus.CLOSED);
        assertThat(reloaded.getReporter().getId()).isEqualTo(citizen.getId());
    }

    @Test
    void rescueCloseRefusesAWasteReport() {
        Report wasteReport = wasteReport();
        reportService.assign(wasteReport, wasteTeam, "Officer");

        assertThatThrownBy(() -> reportService.closeAfterRescue(wasteReport, photo("waste.jpg"), "Crew"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("waste report");
    }

    @Test
    void rescueCloseNeedsAPhotoAndAStartedRescue() {
        Report report = reportService.createAnimal(citizen, animalForm(), null);

        assertThatThrownBy(() -> reportService.closeAfterRescue(report, photo("a.jpg"), "Crew"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Start the rescue");

        startRescue(report);
        assertThatThrownBy(() -> reportService.closeAfterRescue(report, new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]), "Crew"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("after-rescue photo");
    }

    @Test
    void animalReportsNeverEnterTheWasteCollectionPipeline() {
        Report report = reportService.createAnimal(citizen, animalForm(), null);
        startRescue(report);

        assertThatThrownBy(() -> reportService.markCollected(report, 12.0, 1.0, 2.0, "Crew"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("animal rescue report");
    }

    /**
     * A stale or hand-made assignment of waste work to a rescue crew must not reach
     * that crew's queue — that is what put disposal screens in front of rescuers.
     */
    @Test
    void staleWasteAssignmentNeverReachesTheRescueQueue() {
        Report wasteReport = wasteReport();
        CleanVision.domain.Assignment stale = new CleanVision.domain.Assignment();
        stale.setReport(wasteReport);
        stale.setTeam(animalTeam);
        assignments.save(stale);
        assignments.flush();

        assertThat(reportService.byTeam(animalTeam))
                .doesNotContain(wasteReport)
                .allSatisfy(r -> assertThat(r.getWasteType()).isEqualTo(WasteCategory.ANIMAL));
    }

    private void startRescue(Report report) {
        reportService.changeStatus(report, ReportStatus.IN_PROGRESS, "Rescue Crew", "Rescue started");
    }

    private MockMultipartFile photo(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", "photo-bytes".getBytes());
    }

    private AnimalReportForm animalForm() {
        AnimalReportForm form = new AnimalReportForm();
        form.setAnimalType("DOG");
        form.setAnimalCondition("INJURED");
        form.setWard("Ward 9");
        form.setAddress("Near the market");
        form.setDescription("Stray dog hit by a vehicle");
        return form;
    }

    private Team team(String name, TeamType type, String ward) {
        Team team = new Team();
        team.setTeamName(name);
        team.setTeamType(type);
        team.setWard(ward);
        team.setActive(true);
        return teams.save(team);
    }

    private Report wasteReport() {
        Report report = new Report();
        report.setReporter(citizen);
        report.setWasteType(WasteCategory.PLASTIC);
        report.setConfidence(0.9);
        report.setWard("Ward 9");
        report.setStatus(ReportStatus.AI_ANALYZED);
        return reports.save(report);
    }

    private void deactivateOtherTeams() {
        teams.findAll().stream()
                .filter(t -> t.getTeamType() == TeamType.ANIMAL || t.getTeamType() == TeamType.WASTE)
                .filter(t -> !t.getId().equals(animalTeam.getId()) && !t.getId().equals(wasteTeam.getId()))
                .forEach(t -> t.setActive(false));
        teams.flush();
    }

    private User newCitizen() {
        User user = new User();
        user.setName("Rescue Citizen");
        user.setEmail("rescue-citizen@example.com");
        user.setPhone("9876500002");
        user.setRole(Role.CITIZEN);
        user.setPhoneVerified(true);
        return users.save(user);
    }
}
