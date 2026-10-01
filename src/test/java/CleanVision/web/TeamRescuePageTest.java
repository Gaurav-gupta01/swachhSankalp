package CleanVision.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.dto.Forms.AnimalReportForm;
import CleanVision.repository.UserRepository;
import CleanVision.service.ReportService;

/**
 * The pages behind an animal report: the rescue crew's screen never offers the waste
 * collection or disposal pipeline, and the citizen is told the report closed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamRescuePageTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ReportService reportService;

    @Autowired
    private UserRepository users;

    @Autowired
    private CleanVision.repository.ReportRepository reports;

    @Autowired
    private CleanVision.repository.TeamRepository teams;

    private User citizen;
    private Report animalReport;
    private MockHttpSession leaderSession;

    @BeforeEach
    void setUp() throws Exception {
        citizen = users.findByEmailIgnoreCase("citizen@demo.in").orElseThrow();
        animalReport = reportService.createAnimal(citizen, animalForm(), null);

        Team rescueTeam = reportService.activeAssignment(animalReport).getTeam();
        User leader = users.findByEmailIgnoreCase("leader@cleanvision.gov.in").orElseThrow();
        leader.setTeam(rescueTeam);
        users.save(leader);
        leaderSession = signIn("leader@cleanvision.gov.in", "leader123");
    }

    @Test
    void rescueTeamQueueShowsTheAnimalReportAndNoWasteDisposalWork() throws Exception {
        String html = page(leaderSession, "/team");

        assertThat(html).contains(animalReport.getPublicId());
        assertThat(html).contains("Animal reports land here");
        assertThat(html).doesNotContain("Find disposal facility")
                .doesNotContain("Waste collection completed");
    }

    @Test
    void rescueReportPageOffersThePhotoCloseFlowInsteadOfDisposal() throws Exception {
        String html = page(leaderSession, "/team/reports/" + animalReport.getPublicId());

        assertThat(html).contains("Rescue job")
                .contains("Mark rescue started")
                .contains("Upload &amp; close")
                .contains("action=\"/team/reports/" + animalReport.getPublicId() + "/close\"")
                .contains("data-camera-input=\"afterFile\"")
                .contains("Rescue status");
        assertThat(html).doesNotContain("Waste collection completed")
                .doesNotContain("Find disposal facility")
                .doesNotContain("Disposal confirmation")
                .doesNotContain("Approx. quantity (kg)");
    }

    @Test
    void closedReportTellsTheCitizenItClosedSuccessfully() throws Exception {
        reportService.changeStatus(animalReport, ReportStatus.IN_PROGRESS, "Rescue Crew", "Rescue started");
        reportService.closeAfterRescue(animalReport, new org.springframework.mock.web.MockMultipartFile(
                "file", "after.jpg", "image/jpeg", "photo".getBytes()), "Rescue Crew");

        MockHttpSession reporter = signIn("citizen@demo.in", "demo123");
        String html = page(reporter, "/reports/" + animalReport.getPublicId());

        assertThat(html).contains("Report closed successfully");
    }

    @Test
    void wasteReportPageKeepsTheCollectionAndDisposalWorkflow() throws Exception {
        CleanVision.domain.Report wasteReport = new CleanVision.domain.Report();
        wasteReport.setReporter(citizen);
        wasteReport.setWasteType(CleanVision.domain.WasteCategory.PLASTIC);
        wasteReport.setConfidence(0.9);
        wasteReport.setWard("Ward 1");
        wasteReport.setAddress("Bus Stand Back Road");
        wasteReport.setStatus(ReportStatus.AI_ANALYZED);
        wasteReport = reports.save(wasteReport);
        wasteReport.setPublicId("CV" + (20000 + wasteReport.getId()));
        wasteReport = reports.save(wasteReport);

        Team wasteTeam = new Team();
        wasteTeam.setTeamName("Waste Crew Page Test");
        wasteTeam.setTeamType(CleanVision.domain.TeamType.WASTE);
        wasteTeam.setWard("Ward 1");
        wasteTeam.setActive(true);
        wasteTeam = teams.save(wasteTeam);
        reportService.assign(wasteReport, wasteTeam, "Officer");

        User leader = users.findByEmailIgnoreCase("leader@cleanvision.gov.in").orElseThrow();
        leader.setTeam(wasteTeam);
        users.save(leader);
        MockHttpSession session = signIn("leader@cleanvision.gov.in", "leader123");

        String html = page(session, "/team/reports/" + wasteReport.getPublicId());

        assertThat(html).contains("Update this job")
                .contains("Mark work started")
                .contains("Upload after photo")
                .contains("Waste collection completed")
                .contains("data-camera-input=\"afterFile\"");
        assertThat(html).doesNotContain("Upload &amp; close")
                .doesNotContain("Rescue status");
    }

    private MockHttpSession signIn(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/login")
                        .session(session)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection());
        return session;
    }

    private String page(MockHttpSession session, String url) throws Exception {
        MvcResult result = mvc.perform(get(url).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    private AnimalReportForm animalForm() {
        AnimalReportForm form = new AnimalReportForm();
        form.setAnimalType("DOG");
        form.setAnimalCondition("INJURED");
        form.setWard("Ward 2");
        form.setAddress("Near the market");
        form.setDescription("Stray dog hit by a vehicle");
        return form;
    }
}
