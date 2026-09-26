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
import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.domain.WasteCategory;
import CleanVision.domain.Severity;
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
    public String home(Model model) {
        model.addAttribute("stats", statsService.dashboard());
        return "index";
    }

    @GetMapping("/report/new")
    public String newReportPage(Model model) {
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
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        if (user == null) {
            return "redirect:/login?next=/my-reports";
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
        boolean allowed = user != null && (user.getRole() == Role.ADMIN || user.getId().equals(report.getReporter().getId()));
        if (!allowed) {
            throw new AppException("You do not have access to report " + publicId);
        }

        model.addAttribute("report", report);
        model.addAttribute("timeline", reportService.timeline(report));
        model.addAttribute("assignment", reportService.activeAssignment(report));
        return "report/detail";
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
