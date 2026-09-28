package CleanVision.config;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

import javax.imageio.ImageIO;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Role;
import CleanVision.domain.Severity;
import CleanVision.domain.StatusHistory;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.repository.ReportRepository;
import CleanVision.repository.StatusHistoryRepository;
import CleanVision.repository.TeamRepository;
import CleanVision.repository.UserRepository;
import CleanVision.security.PasswordHasher;
import CleanVision.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds a demo administrator, a citizen account, cleanup teams and a handful of
 * sample reports so the municipal dashboard is never empty on first launch.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private static final double BASE_LAT = 15.2750;
    private static final double BASE_LNG = 73.9570;

    static final String SUPER_ADMIN_EMAIL = "super@cleanvision.gov.in";
    static final String SUPER_ADMIN_PASSWORD = "super123";

    static final String LEADER_EMAIL = "leader@cleanvision.gov.in";
    static final String LEADER_PASSWORD = "leader123";

    private final UserRepository users;
    private final TeamRepository teams;
    private final ReportRepository reports;
    private final StatusHistoryRepository history;
    private final CleanVision.repository.AssignmentRepository assignments;
    private final PasswordHasher hasher;
    private final ImageStorageService images;

    @Override
    public void run(ApplicationArguments args) {
        boolean fresh = users.count() == 0;
        ensureSuperAdmin();
        if (!fresh) {
            ensureTeamLeader();
            return;
        }

        User admin = user("Municipal Admin", "admin@cleanvision.gov.in", "admin123", Role.ADMIN);
        User citizen = user("Demo Citizen", "citizen@demo.in", "demo123", Role.CITIZEN);

        List<Team> seedTeams = List.of(
                team("Team A", "Ward 1", "Rahul Sawant", "9822011111"),
                team("Team B", "Ward 5", "Priya Naik", "9822022222"),
                team("Team C", "Ward 3", "Imran Shaikh", "9822033333"));

        record Seed(WasteCategory category, Severity severity, String ward, String address,
                ReportStatus status, String description, long ageHours) {
        }

        List<Seed> seeds = List.of(
                new Seed(WasteCategory.PLASTIC, Severity.HIGH, "Ward 5", "Near Central Bus Stand, Margao",
                        ReportStatus.AI_ANALYZED, "Bottles and food packaging dumped beside the footpath", 1),
                new Seed(WasteCategory.ORGANIC, Severity.MEDIUM, "Ward 2", "Market Road, Margao",
                        ReportStatus.ASSIGNED, "Vegetable waste spilling out of the community bin", 5),
                new Seed(WasteCategory.E_WASTE, Severity.HIGH, "Ward 3", "Electricity Board Lane, Margao",
                        ReportStatus.IN_PROGRESS, "Old chargers, cables and circuit boards left near the transformer", 9),
                new Seed(WasteCategory.CONSTRUCTION, Severity.HIGH, "Ward 5", "Behind Municipal Garden, Margao",
                        ReportStatus.ASSIGNED, "Bricks and cement debris blocking the service lane", 20),
                new Seed(WasteCategory.MIXED, Severity.MEDIUM, "Ward 1", "Bus Stand Back Road, Margao",
                        ReportStatus.IN_PROGRESS, "Household waste mixed with plastic near the school gate", 27),
                new Seed(WasteCategory.PAPER, Severity.LOW, "Ward 3", "Print Market, Margao",
                        ReportStatus.RESOLVED, "Cartons and newspapers piled next to the recycling skip", 33),
                new Seed(WasteCategory.PLASTIC, Severity.MEDIUM, "Ward 4", "River Bridge Approach, Margao",
                        ReportStatus.RESOLVED, "Plastic bags caught in the railing after rain", 40),
                new Seed(WasteCategory.GLASS, Severity.MEDIUM, "Ward 2", "Wine Shop Corner, Margao",
                        ReportStatus.VERIFIED, "Broken bottles scattered on the footpath", 48),
                new Seed(WasteCategory.METAL, Severity.LOW, "Ward 1", "Workshop Lane, Margao",
                        ReportStatus.SUBMITTED, "Rusted cans and scrap metal near the drain", 54),
                new Seed(WasteCategory.MIXED, Severity.HIGH, "Ward 5", "Vacant Plot, Margao", ReportStatus.SUBMITTED,
                        "Large unauthorised dump of mixed waste on a vacant plot", 62));

        Random random = new Random(42);
        int index = 0;
        for (Seed seed : seeds) {
            index++;
            Report report = new Report();
            report.setReporter(citizen);
            report.setImageUrl(generateImage(seed.category(), random));
            report.setWasteType(seed.category());
            report.setConfidence(0.72 + random.nextDouble() * 0.24);
            report.setSeverity(seed.severity());
            report.setWard(seed.ward());
            report.setAddress(seed.address());
            report.setDescription(seed.description());
            report.setLatitude(BASE_LAT + (random.nextDouble() - 0.5) * 0.02);
            report.setLongitude(BASE_LNG + (random.nextDouble() - 0.5) * 0.02);
            report.setStatus(ReportStatus.SUBMITTED);
            report.setCreatedAt(LocalDateTime.now().minusHours(seed.ageHours()));
            report = reports.save(report);

            report.setPublicId("CV" + (10000 + report.getId()));
            report = reports.save(report);

            recordEntry(report, ReportStatus.SUBMITTED, citizen.getName(), "Report submitted by citizen");
            recordEntry(report, ReportStatus.AI_ANALYZED, "Swachh Sankalp AI",
                    seed.category().getLabel() + " detected at "
                            + Math.round(report.getConfidence() * 100) + "% confidence");

            ReportStatus target = seed.status();
            report.setStatus(target);
            report = reports.save(report);

            if (target.ordinal() >= ReportStatus.ASSIGNED.ordinal()) {
                int slot = index;
                Team team = teams.findByWardAndActiveTrue(report.getWard()).stream().findFirst()
                        .orElseGet(() -> seedTeams.get(slot % seedTeams.size()));
                CleanVision.domain.Assignment assignment = new CleanVision.domain.Assignment();
                assignment.setReport(report);
                assignment.setTeam(team);
                assignments.save(assignment);
                recordEntry(report, ReportStatus.ASSIGNED, admin.getName(),
                        "Assigned to " + team.getTeamName());
            }

            for (ReportStatus step : ReportStatus.workflow()) {
                if (step.ordinal() <= ReportStatus.AI_ANALYZED.ordinal()) {
                    continue;
                }
                if (step.ordinal() > target.ordinal()) {
                    break;
                }
                if (step == ReportStatus.ASSIGNED) {
                    continue;
                }
                report.setStatus(step);
                report = reports.save(report);
                recordEntry(report, step, admin.getName(), "Status updated to " + step.getLabel());
            }

            if (target == ReportStatus.RESOLVED || target == ReportStatus.CLOSED) {
                report.setResolvedAt(report.getCreatedAt().plusHours(6));
                report = reports.save(report);
            }
        }

        log.info("Seeded demo data - officer {} / citizen {} and {} sample reports",
                admin.getEmail(), citizen.getEmail(), reports.count());

        ensureTeamLeader();
    }

    private void ensureSuperAdmin() {
        if (users.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL).isPresent()) {
            return;
        }
        User superAdmin = user("Super Admin", SUPER_ADMIN_EMAIL, SUPER_ADMIN_PASSWORD, Role.SUPER_ADMIN);
        log.info("Created super admin account {}", superAdmin.getEmail());
    }

    private void ensureTeamLeader() {
        if (users.countByRole(Role.TEAM_LEADER) > 0) {
            return;
        }
        Team team = teams.findByActiveTrueOrderByTeamNameAsc().stream().findFirst().orElse(null);
        if (team == null) {
            return;
        }
        User leader = user("Team Leader", LEADER_EMAIL, LEADER_PASSWORD, Role.TEAM_LEADER);
        leader.setTeam(team);
        users.save(leader);
        log.info("Created team leader account {} for {}", leader.getEmail(), team.getTeamName());
    }

    private User user(String name, String email, String password, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);
        user.setPasswordHash(hasher.hash(password));
        user.setPhone("9822000000");
        return users.save(user);
    }

    private Team team(String name, String ward, String leader, String contact) {
        Team team = new Team();
        team.setTeamName(name);
        team.setWard(ward);
        team.setLeader(leader);
        team.setContact(contact);
        return teams.save(team);
    }

    private void recordEntry(Report report, ReportStatus status, String updatedBy, String note) {
        StatusHistory entry = new StatusHistory();
        entry.setReport(report);
        entry.setStatus(status);
        entry.setUpdatedBy(updatedBy);
        entry.setNote(note);
        entry.setTimestamp(report.getCreatedAt());
        history.save(entry);
    }

    private String generateImage(WasteCategory category, Random random) {
        BufferedImage image = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        Color[] palette = switch (category) {
            case PLASTIC -> new Color[] { new Color(30, 90, 200), new Color(90, 170, 240), Color.WHITE,
                    new Color(240, 240, 250), new Color(20, 140, 90) };
            case ORGANIC -> new Color[] { new Color(60, 130, 40), new Color(120, 160, 40), new Color(150, 110, 50),
                    new Color(90, 70, 30), new Color(200, 180, 60) };
            case PAPER -> new Color[] { new Color(210, 180, 130), new Color(235, 225, 205), new Color(180, 150, 110),
                    Color.WHITE, new Color(245, 240, 230) };
            case GLASS -> new Color[] { new Color(220, 235, 240), Color.WHITE, new Color(170, 205, 215),
                    new Color(200, 220, 210), new Color(245, 250, 250) };
            case METAL -> new Color[] { new Color(120, 125, 130), new Color(180, 185, 190), new Color(70, 72, 76),
                    new Color(200, 200, 205), new Color(95, 100, 105) };
            case E_WASTE -> new Color[] { new Color(20, 20, 24), new Color(50, 55, 60), new Color(30, 110, 60),
                    new Color(180, 40, 40), new Color(220, 180, 40) };
            case CONSTRUCTION -> new Color[] { new Color(150, 145, 135), new Color(175, 90, 70), new Color(200, 190, 175),
                    new Color(120, 115, 105), new Color(220, 215, 205) };
            case MIXED, OTHER -> new Color[] { new Color(60, 120, 200), new Color(90, 150, 60), new Color(200, 170, 60),
                    new Color(180, 70, 70), new Color(140, 140, 140) };
        };

        Color background = switch (category) {
            case E_WASTE -> new Color(35, 35, 40);
            case CONSTRUCTION -> new Color(170, 165, 155);
            case PAPER -> new Color(225, 218, 205);
            case ORGANIC -> new Color(120, 130, 90);
            default -> new Color(145, 150, 155);
        };
        g.setColor(background);
        g.fillRect(0, 0, 800, 600);

        int blobs = 26 + random.nextInt(26);
        for (int i = 0; i < blobs; i++) {
            g.setColor(palette[random.nextInt(palette.length)]);
            int w = 40 + random.nextInt(160);
            int h = 30 + random.nextInt(120);
            int x = random.nextInt(800) - 40;
            int y = random.nextInt(600) - 30;
            if (random.nextBoolean()) {
                g.fillOval(x, y, w, h);
            } else {
                g.fillRect(x, y, w, h);
            }
        }

        g.setColor(new Color(0, 0, 0, 40));
        g.fillRect(0, 500, 800, 100);
        g.dispose();

        String filename = java.util.UUID.randomUUID().toString().replace("-", "") + ".jpg";
        Path target = images.resolve(filename);
        try {
            ImageIO.write(image, "jpg", target.toFile());
        } catch (IOException ex) {
            log.warn("Could not write seeded sample image {}", filename);
            return "missing.jpg";
        }
        return filename;
    }
}
