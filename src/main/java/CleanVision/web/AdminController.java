package CleanVision.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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
        model.addAttribute("teams", teams.findByActiveTrueOrderByTeamNameAsc());
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

        reportService.assign(report, team, actor(user));
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

    @PostMapping("/reports/{publicId}/delete")
    public String delete(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Report report = reportService.byPublicId(publicId);
        if (user == null || user.getRole() != CleanVision.domain.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        reportService.delete(report);
        redirect.addFlashAttribute("flashMessage", publicId + " deleted");
        return "redirect:/admin/reports";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        model.addAttribute("stats", statsService.dashboard());
        model.addAttribute("geoPoints", statsService.allReportsGeo());
        return "admin/analytics";
    }

    @GetMapping("/teams")
    public String teams(Model model) {
        List<Team> all = teams.findByActiveTrueOrderByTeamNameAsc();
        model.addAttribute("teams", all);
        model.addAttribute("workload", all.stream()
                .collect(java.util.stream.Collectors.toMap(
                        t -> t.getId(),
                        t -> reportService.activeAssignmentCount(t),
                        (a, b) -> a,
                        java.util.LinkedHashMap::new)));
        return "admin/teams";
    }

    private String actor(User user) {
        return user == null ? "System" : user.getName();
    }
}
