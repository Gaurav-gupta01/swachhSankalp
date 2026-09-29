package CleanVision.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Severity;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.repository.TeamRepository;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.ReportService;
import CleanVision.service.StatsService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final StatsService statsService;
    private final ReportService reportService;
    private final TeamRepository teams;
    private final CleanVision.repository.AssignmentRepository assignments;
    private final CleanVision.repository.UserRepository users;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", statsService.dashboard());
        model.addAttribute("geoPoints", statsService.allReportsGeo());
        return "admin/dashboard";
    }

    @GetMapping("/reports")
    public String reports(@RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String ward,
            Model model) {
        List<Report> all = reportService.all();

        List<Report> filtered = all.stream()
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equals(status))
                .filter(r -> severity == null || severity.isBlank() || r.getSeverity().name().equals(severity))
                .filter(r -> ward == null || ward.isBlank()
                        || (r.getWard() != null && r.getWard().equalsIgnoreCase(ward)))
                .toList();

        model.addAttribute("reports", filtered);
        model.addAttribute("selectedStatus", status == null ? "" : status);
        model.addAttribute("selectedSeverity", severity == null ? "" : severity);
        model.addAttribute("selectedWard", ward == null ? "" : ward);
        model.addAttribute("statuses", ReportStatus.workflow());
        model.addAttribute("severities", Severity.values());
        model.addAttribute("wards", all.stream().map(Report::getWard)
                .filter(w -> w != null && !w.isBlank()).distinct().sorted().toList());
        return "admin/reports";
    }

    @GetMapping("/reports/{publicId}")
    public String reportDetail(@PathVariable String publicId, Model model) {
        Report report = reportService.byPublicId(publicId);
        model.addAttribute("report", report);
        model.addAttribute("timeline", reportService.timeline(report));
        model.addAttribute("assignment", reportService.activeAssignment(report));
        model.addAttribute("teams", assignableTeams(report));
        model.addAttribute("statuses", ReportStatus.workflow());
        return "admin/report-detail";
    }

    @PostMapping("/reports/{publicId}/assign")
    public String assign(@PathVariable String publicId,
            @RequestParam Long teamId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Report report = reportService.byPublicId(publicId);
        Team team = teams.findById(teamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));

        try {
            reportService.assign(report, team, actor(user));
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/reports/" + publicId;
        }
        redirect.addFlashAttribute("flashMessage", publicId + " assigned to " + team.getTeamName());
        return "redirect:/admin/reports/" + publicId;
    }

    @PostMapping("/reports/{publicId}/status")
    public String changeStatus(@PathVariable String publicId,
            @RequestParam String status,
            @RequestParam(required = false) String note,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Report report = reportService.byPublicId(publicId);
        ReportStatus target;
        try {
            target = ReportStatus.valueOf(status);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }

        reportService.changeStatus(report, target, actor(user),
                note == null || note.isBlank() ? "Status updated to " + target.getLabel() : note);
        redirect.addFlashAttribute("flashMessage", publicId + " moved to " + target.getLabel());
        return "redirect:/admin/reports/" + publicId;
    }

    @PostMapping("/reports/{publicId}/after")
    public String uploadAfter(@PathVariable String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Report report = reportService.byPublicId(publicId);
        try {
            reportService.attachAfterImage(report, file, actor(user));
            redirect.addFlashAttribute("flashMessage", "After-cleanup photo added to " + publicId);
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/reports/" + publicId;
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("stats", statsService.dashboard());
        model.addAttribute("geoPoints", statsService.allReportsGeo());
        return "admin/analytics";
    }

    @GetMapping("/teams")
    public String teams(Model model) {
        List<Team> all = teams.findAll(org.springframework.data.domain.Sort.by("teamName"));
        model.addAttribute("teams", all);
        model.addAttribute("workload", all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> t.getId(),
                        t -> reportService.activeAssignmentCount(t),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new)));

        java.util.Map<Long, String> leaderLogins = new java.util.LinkedHashMap<>();
        for (Team team : all) {
            leaderLogins.put(team.getId(),
                    users.findFirstByTeamIdAndRole(team.getId(), CleanVision.domain.Role.TEAM_LEADER)
                            .map(User::getEmail)
                            .orElse(null));
        }
        model.addAttribute("leaderLogins", leaderLogins);
        return "admin/teams";
    }

    @GetMapping("/teams/new")
    public String newTeam(Model model) {
        if (!model.containsAttribute("form")) {
            CleanVision.dto.Forms.TeamForm form = new CleanVision.dto.Forms.TeamForm();
            form.setActive(true);
            model.addAttribute("form", form);
        }
        model.addAttribute("editing", false);
        model.addAttribute("teamId", null);
        model.addAttribute("teamTypes", CleanVision.domain.TeamType.values());
        return "admin/team-form";
    }

    @PostMapping("/teams")
    public String createTeam(@ModelAttribute("form") CleanVision.dto.Forms.TeamForm form,
            RedirectAttributes redirect) {
        if (form.getTeamName() == null || form.getTeamName().isBlank()) {
            redirect.addFlashAttribute("errorMessage", "Team name is required");
            return "redirect:/admin/teams/new";
        }
        Team team = new Team();
        applyTeamForm(team, form);
        teams.save(team);
        redirect.addFlashAttribute("flashMessage", team.getTeamName() + " created");
        return "redirect:/admin/teams";
    }

    @GetMapping("/teams/{id}/edit")
    public String editTeam(@PathVariable Long id, Model model, RedirectAttributes redirect) {
        Team team = teams.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (!model.containsAttribute("form")) {
            CleanVision.dto.Forms.TeamForm form = new CleanVision.dto.Forms.TeamForm();
            form.setTeamName(team.getTeamName());
            form.setWard(team.getWard());
            form.setLeader(team.getLeader());
            form.setContact(team.getContact());
            form.setActive(team.isActive());
            form.setTeamType(team.getTeamType() == null
                    ? CleanVision.domain.TeamType.WASTE.name()
                    : team.getTeamType().name());
            model.addAttribute("form", form);
        }
        model.addAttribute("editing", true);
        model.addAttribute("teamId", team.getId());
        model.addAttribute("teamTypes", CleanVision.domain.TeamType.values());
        return "admin/team-form";
    }

    @PostMapping("/teams/{id}")
    public String updateTeam(@PathVariable Long id,
            @ModelAttribute("form") CleanVision.dto.Forms.TeamForm form,
            RedirectAttributes redirect) {
        Team team = teams.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        if (form.getTeamName() == null || form.getTeamName().isBlank()) {
            redirect.addFlashAttribute("errorMessage", "Team name is required");
            return "redirect:/admin/teams/" + id + "/edit";
        }
        applyTeamForm(team, form);
        teams.save(team);
        redirect.addFlashAttribute("flashMessage", team.getTeamName() + " updated");
        return "redirect:/admin/teams";
    }

    @PostMapping("/teams/{id}/delete")
    public String deleteTeam(@PathVariable Long id, RedirectAttributes redirect) {
        Team team = teams.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
        long used = assignments.countByTeamId(team.getId());
        if (used > 0) {
            redirect.addFlashAttribute("errorMessage",
                    team.getTeamName() + " is linked to " + used
                            + " report assignment(s). Reassign those reports before deleting the team.");
            return "redirect:/admin/teams";
        }
        teams.delete(team);
        redirect.addFlashAttribute("flashMessage", team.getTeamName() + " deleted");
        return "redirect:/admin/teams";
    }

    private void applyTeamForm(Team team, CleanVision.dto.Forms.TeamForm form) {
        team.setTeamName(form.getTeamName().trim());
        team.setWard(form.getWard() == null ? null : form.getWard().trim());
        team.setLeader(form.getLeader() == null ? null : form.getLeader().trim());
        team.setContact(form.getContact() == null ? null : form.getContact().trim());
        team.setActive(form.isActive());
        team.setTeamType(CleanVision.domain.TeamType.fromApi(form.getTeamType()));
    }

    /** Waste reports go to waste crews, animal reports to animal rescue crews. */
    private List<Team> assignableTeams(Report report) {
        CleanVision.domain.TeamType type = report.getWasteType() == CleanVision.domain.WasteCategory.ANIMAL
                ? CleanVision.domain.TeamType.ANIMAL
                : CleanVision.domain.TeamType.WASTE;
        return teams.findByActiveTrueAndTeamTypeOrderByTeamNameAsc(type);
    }

    private String actor(User user) {
        return user == null ? "System" : user.getName();
    }
}
