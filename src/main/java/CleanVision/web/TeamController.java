package CleanVision.web;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.ReportService;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/team")
@RequiredArgsConstructor
public class TeamController {

    private final ReportService reportService;

    @GetMapping
    public String dashboard(Model model,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        Team team = teamOf(user);
        List<Report> reports = reportService.byTeam(team);

        model.addAttribute("team", team);
        model.addAttribute("reports", reports);
        model.addAttribute("openCount", reports.stream()
                .filter(r -> r.getStatus().ordinal() < ReportStatus.RESOLVED.ordinal()).count());
        model.addAttribute("inProgressCount", reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.IN_PROGRESS).count());
        model.addAttribute("resolvedCount", reports.stream()
                .filter(r -> r.getStatus() == ReportStatus.RESOLVED || r.getStatus() == ReportStatus.CLOSED)
                .count());
        return "team/dashboard";
    }

    @GetMapping("/reports/{publicId}")
    public String reportDetail(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            Model model) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        model.addAttribute("report", report);
        model.addAttribute("team", team);
        model.addAttribute("timeline", reportService.timeline(report));
        model.addAttribute("assignment", reportService.activeAssignment(report));
        model.addAttribute("mapPoints", List.of(mapPoint(report)));
        model.addAttribute("directionsUrl", directionsUrl(report));
        return "team/report-detail";
    }

    @PostMapping("/reports/{publicId}/start")
    public String startWork(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        if (report.getStatus().ordinal() >= ReportStatus.RESOLVED.ordinal()) {
            redirect.addFlashAttribute("errorMessage", publicId + " is already "
                    + report.getStatus().getLabel().toLowerCase() + ".");
            return "redirect:/team/reports/" + publicId;
        }
        reportService.changeStatus(report, ReportStatus.IN_PROGRESS, user.getName(),
                "Cleanup started by " + team.getTeamName());
        redirect.addFlashAttribute("flashMessage", publicId + " marked as In Progress");
        return "redirect:/team/reports/" + publicId;
    }

    @PostMapping("/reports/{publicId}/after")
    public String uploadAfter(@PathVariable String publicId,
            @RequestParam("file") MultipartFile file,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        Team team = teamOf(user);
        Report report = reportService.byPublicId(publicId);
        requireAssignedTo(team, report);

        try {
            reportService.attachAfterImage(report, file, user.getName());
            redirect.addFlashAttribute("flashMessage",
                    "After-cleanup photo added to " + publicId + " — report resolved");
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/team/reports/" + publicId;
    }

    private Team teamOf(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (user.getTeam() == null) {
            throw new AppException("Your account is not linked to a cleanup team yet.");
        }
        return user.getTeam();
    }

    private void requireAssignedTo(Team team, Report report) {
        CleanVision.domain.Assignment assignment = reportService.activeAssignment(report);
        if (assignment == null || assignment.getTeam() == null
                || !assignment.getTeam().getId().equals(team.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    report.getPublicId() + " is not assigned to your team");
        }
    }

    private Map<String, Object> mapPoint(Report report) {
        Map<String, Object> point = new LinkedHashMap<>();
        point.put("publicId", report.getPublicId());
        point.put("wasteType", report.getWasteType().getLabel());
        point.put("severity", report.getSeverity().name());
        point.put("severityLabel", report.getSeverity().getLabel());
        point.put("status", report.getStatus().getLabel());
        point.put("statusCode", report.getStatus().name());
        point.put("ward", report.getWard() == null ? "" : report.getWard());
        point.put("latitude", report.getLatitude());
        point.put("longitude", report.getLongitude());
        return point;
    }

    private String directionsUrl(Report report) {
        String destination;
        if (report.getLatitude() != null && report.getLongitude() != null) {
            destination = report.getLatitude() + "," + report.getLongitude();
        } else if (report.getAddress() != null && !report.getAddress().isBlank()) {
            String address = report.getAddress()
                    + (report.getWard() == null || report.getWard().isBlank() ? "" : ", " + report.getWard());
            destination = URLEncoder.encode(address, StandardCharsets.UTF_8);
        } else {
            return null;
        }
        return "https://www.google.com/maps/dir/?api=1&destination=" + destination;
    }
}
