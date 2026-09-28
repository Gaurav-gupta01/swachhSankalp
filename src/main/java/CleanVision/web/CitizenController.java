package CleanVision.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.ai.AiResult;
import CleanVision.domain.Report;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.domain.Severity;
import CleanVision.dto.Forms.DashboardStatistics;
import CleanVision.dto.Forms.ReportForm;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.ImageStorageService;
import CleanVision.service.ReportService;
import CleanVision.service.StatsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CitizenController {

    private static final String AI_SESSION_PREFIX = "cvAi:";

    private final ReportService reportService;
    private final StatsService statsService;
    private final ImageStorageService images;

    @GetMapping("/")
    public String home(Model model,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        if (user != null) {
            return "redirect:" + user.getRole().dashboardPath();
        }
        DashboardStatistics stats = statsService.dashboard();
        model.addAttribute("stats", stats);
        model.addAttribute("geoPoints", statsService.allReportsGeo());

        Report showcase = reportService.latestCompletedWithAfterImage();
        if (showcase != null) {
            model.addAttribute("beforeImage", showcase.getImageUrl());
            model.addAttribute("afterImage", showcase.getAfterImageUrl());
        } else if (!stats.recentReports().isEmpty()) {
            model.addAttribute("beforeImage", stats.recentReports().get(0).get("imageUrl"));
        }
        return "index";
    }

    @GetMapping("/report/new")
    public String newReportPage(Model model,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        if (cannotReport(user)) {
            redirect.addFlashAttribute("errorMessage",
                    "Only citizen accounts can report waste. Staff accounts manage reports instead.");
            return staffHome(user);
        }
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new ReportForm());
        }
        return "report/new";
    }

    @PostMapping("/report")
    public String submitReport(@ModelAttribute("form") ReportForm form,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            HttpSession session,
            RedirectAttributes redirect) {
        if (user == null) {
            return "redirect:/login?next=/report/new";
        }
        if (cannotReport(user)) {
            redirect.addFlashAttribute("errorMessage",
                    "Only citizen accounts can report waste. Staff accounts manage reports instead.");
            return staffHome(user);
        }

        try {
            AiResult stored = (AiResult) session.getAttribute(AI_SESSION_PREFIX + form.getImageFile());
            AiResult ai = rebuild(stored, form);

            Report report = reportService.create(user, form, ai);
            session.removeAttribute(AI_SESSION_PREFIX + form.getImageFile());

            redirect.addFlashAttribute("flashMessage",
                    "Report " + report.getPublicId() + " received. We will keep you updated.");
            return "redirect:/my-reports";
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/report/new";
        }
    }

    @GetMapping("/my-reports")
    public String myReports(Model model,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        if (user == null) {
            return "redirect:/login?next=/my-reports";
        }
        if (cannotReport(user)) {
            redirect.addFlashAttribute("errorMessage", "Staff accounts do not file reports.");
            return staffHome(user);
        }
        List<Report> reports = reportService.byUser(user);
        model.addAttribute("reports", reports);
        model.addAttribute("statuses", CleanVision.domain.ReportStatus.workflow());
        return "report/my-reports";
    }

    @GetMapping("/reports/{publicId}")
    public String reportDetail(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            Model model) {
        Report report = reportService.byPublicId(publicId);
        boolean allowed = user != null
                && (user.getRole().isOfficer() || user.getId().equals(report.getReporter().getId()));
        if (!allowed) {
            throw new AppException("You do not have access to report " + publicId);
        }

        model.addAttribute("report", report);
        model.addAttribute("timeline", reportService.timeline(report));
        model.addAttribute("assignment", reportService.activeAssignment(report));
        return "report/detail";
    }

    @PostMapping("/reports/{publicId}/delete")
    public String deleteOwnReport(@PathVariable String publicId,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            RedirectAttributes redirect) {
        if (user == null) {
            return "redirect:/login?next=/reports/" + publicId;
        }
        if (cannotReport(user)) {
            redirect.addFlashAttribute("errorMessage",
                    "Report deletion belongs to citizens. Staff accounts cannot delete reports.");
            return staffHome(user);
        }

        Report report = reportService.byPublicId(publicId);
        if (!user.getId().equals(report.getReporter().getId())) {
            redirect.addFlashAttribute("errorMessage", "You can only delete your own reports.");
            return "redirect:/my-reports";
        }

        reportService.delete(report);
        redirect.addFlashAttribute("flashMessage", "Report " + publicId + " deleted permanently.");
        return "redirect:/my-reports";
    }

    private boolean cannotReport(User user) {
        return user != null && !user.getRole().canReport();
    }

    private String staffHome(User user) {
        return "redirect:" + user.getRole().dashboardPath();
    }

    private AiResult rebuild(AiResult stored, ReportForm form) {
        WasteCategory category = WasteCategory.fromApi(form.getWasteType());
        if (stored == null) {
            return new AiResult(category, form.getConfidence(), Severity.fromApi(form.getSeverity()),
                    "Classification confirmed by the reporter", List.of());
        }
        boolean manuallyCorrected = category != stored.category();
        String reason = manuallyCorrected
                ? stored.reason() + " (category corrected by the reporter)"
                : stored.reason();
        double confidence = manuallyCorrected ? Math.min(stored.confidence(), 0.99) : stored.confidence();
        Severity severity = form.getSeverity() == null || form.getSeverity().isBlank()
                ? stored.severity()
                : Severity.fromApi(form.getSeverity());
        return new AiResult(category, confidence, severity, reason, stored.candidates());
    }
}
