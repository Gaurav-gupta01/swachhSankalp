package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Role;
import CleanVision.domain.Team;
import CleanVision.domain.TeamType;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.dto.Forms.AnimalReportForm;
import CleanVision.repository.TeamRepository;
import CleanVision.repository.UserRepository;

@SpringBootTest
@Transactional
class AnimalReportAssignmentTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private UserRepository users;

    @Autowired
    private CleanVision.repository.ReportRepository reportRepository;

    private User citizen;
    private Team wasteTeam;
    private Team animalTeam;

    @BeforeEach
    void setUp() {
        citizen = users.findByEmailIgnoreCase("citizen@demo.in").orElseGet(this::newCitizen);

        // Start every scenario from a known set of crews.
        wasteTeam = team("Waste Crew", TeamType.WASTE, "Ward 9");
        animalTeam = team("Rescue Crew", TeamType.ANIMAL, "Ward 9");
        deactivateOtherTeams();
    }

    @Test
    void animalReportIsAutoAssignedToAnAnimalRescueTeam() {
        Report report = reportService.createAnimal(citizen, animalForm("Ward 9"), null);

        assertThat(report.getWasteType()).isEqualTo(WasteCategory.ANIMAL);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.ASSIGNED);

        var assignment = reportService.activeAssignment(report);
        assertThat(assignment).isNotNull();
        assertThat(assignment.getTeam().getTeamType()).isEqualTo(TeamType.ANIMAL);
        assertThat(assignment.getTeam().getId()).isEqualTo(animalTeam.getId());
    }

    @Test
    void wasteReportRefusesAnimalRescueTeam() {
        Report report = wasteReport();

        assertThatThrownBy(() -> reportService.assign(report, animalTeam, "Officer"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("animal rescue team and cannot take");
    }

    @Test
    void animalReportRefusesWasteCrew() {
        Report report = reportService.createAnimal(citizen, animalForm("Ward 9"), null);

        assertThatThrownBy(() -> reportService.assign(report, wasteTeam, "Officer"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("waste cleanup team and cannot take");
    }

    @Test
    void reportStaysUnassignedWhenTheCityHasNoRescueTeam() {
        teams.findAll().forEach(team -> team.setActive(false));
        teams.flush();

        Report report = reportService.createAnimal(citizen, animalForm("Ward 9"), null);

        assertThat(report.getStatus()).isEqualTo(ReportStatus.SUBMITTED);
        assertThat(reportService.activeAssignment(report)).isNull();

        // …and an officer still cannot hand it to a waste crew by mistake.
        assertThatThrownBy(() -> reportService.assign(report, wasteTeam, "Officer"))
                .isInstanceOf(AppException.class);
    }

    @Test
    void animalReportsAreNeverGivenToWasteCrewsByTheSeederEither() {
        List<Team> animalTeams = teams.findByActiveTrueAndTeamTypeOrderByTeamNameAsc(TeamType.ANIMAL);
        List<Team> wasteTeams = teams.findByActiveTrueAndTeamTypeOrderByTeamNameAsc(TeamType.WASTE);

        assertThat(animalTeams).isNotEmpty();
        assertThat(wasteTeams).isNotEmpty();
        assertThat(animalTeams).allSatisfy(team -> assertThat(team.getTeamType()).isEqualTo(TeamType.ANIMAL));
    }

    private AnimalReportForm animalForm(String ward) {
        AnimalReportForm form = new AnimalReportForm();
        form.setAnimalType("DOG");
        form.setAnimalCondition("INJURED");
        form.setWard(ward);
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

    /** A waste report built straight from the repository — no photo needed. */
    private Report wasteReport() {
        CleanVision.domain.Report report = new CleanVision.domain.Report();
        report.setReporter(citizen);
        report.setWasteType(WasteCategory.PLASTIC);
        report.setConfidence(0.9);
        report.setWard("Ward 9");
        report.setStatus(ReportStatus.AI_ANALYZED);
        return reportRepository.save(report);
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
        user.setName("Test Citizen");
        user.setEmail("test-citizen@example.com");
        user.setPhone("9876500001");
        user.setRole(Role.CITIZEN);
        user.setPhoneVerified(true);
        return users.save(user);
    }
}
