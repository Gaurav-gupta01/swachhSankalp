package CleanVision.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import CleanVision.ai.AiClassifier;
import CleanVision.ai.AiResult;
import CleanVision.domain.Assignment;
import CleanVision.domain.Report;
import CleanVision.domain.ReportStatus;
import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.dto.Forms.AiClassifyResponse;
import CleanVision.dto.Forms.ApiResponse;
import CleanVision.dto.Forms.LoginForm;
import CleanVision.dto.Forms.RegisterForm;
import CleanVision.dto.Forms.ReportForm;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.AuthService;
import CleanVision.service.ImageStorageService;
import CleanVision.service.ReportService;
import CleanVision.service.StatsService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private static final String AI_SESSION_PREFIX = "cvAi:";

    private final AiClassifier classifier;
    private final ImageStorageService images;
    private final ReportService reportService;
    private final StatsService statsService;
    private final AuthService authService;

    public record StatusRequest(String status, String note) {
    }

    @PostMapping("/auth/register")
    public ApiResponse register(@RequestBody RegisterForm form, HttpSession session) {
        try {
            User user = authService.register(form);
            session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
            return ApiResponse.ok("Account created", publicUser(user));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    @PostMapping("/auth/login")
    public ApiResponse login(@RequestBody LoginForm form, HttpSession session) {
        try {
            User user = authService.authenticate(form);
            session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
            return ApiResponse.ok("Logged in", publicUser(user));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    @PostMapping("/ai/classify")
    public AiClassifyResponse classify(@RequestParam("image") MultipartFile image, HttpSession session) {
        String filename = images.store(image);
        AiResult result;
        try {
            result = classifier.classify(image.getInputStream());
        } catch (java.io.IOException ex) {
            result = AiResult.fallback("Could not read the image: " + ex.getMessage());
        }
        session.setAttribute(AI_SESSION_PREFIX + filename, result);
        return AiClassifyResponse.from(filename, result);
    }

    @PostMapping("/reports")
    public ApiResponse createReport(@ModelAttribute ReportForm form,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            HttpSession session) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        try {
            AiResult stored = (AiResult) session.getAttribute(AI_SESSION_PREFIX + form.getImageFile());
            AiResult ai = stored != null
                    ? stored
                    : new AiResult(CleanVision.domain.WasteCategory.fromApi(form.getWasteType()),
                            form.getConfidence(),
                            CleanVision.domain.Severity.fromApi(form.getSeverity()),
                            "Classification confirmed by the reporter", List.of());
            Report report = reportService.create(user, form, ai);
            session.removeAttribute(AI_SESSION_PREFIX + form.getImageFile());
            return ApiResponse.ok("Report created", reportMap(report));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    @GetMapping("/reports")
    public ApiResponse listReports(
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        List<Map<String, Object>> data = (user.getRole() == Role.ADMIN ? reportService.all()
                : reportService.byUser(user))
                .stream().map(this::reportMap).toList();
        return ApiResponse.ok("Reports", data);
    }

    @GetMapping("/reports/{id}")
    public ApiResponse getReport(@PathVariable String id,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        Report report = find(id);
        if (user == null || (user.getRole() != Role.ADMIN && !user.getId().equals(report.getReporter().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your report");
        }
        Map<String, Object> data = reportMap(report);
        data.put("timeline", reportService.timeline(report).stream().map(entry -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", entry.getStatus().getLabel());
            row.put("updatedBy", entry.getUpdatedBy());
            row.put("note", entry.getNote());
            row.put("timestamp", entry.getTimestamp());
            return row;
        }).toList());
        return ApiResponse.ok("Report", data);
    }

    @PutMapping("/reports/{id}/status")
    public ApiResponse updateStatus(@PathVariable String id, @RequestBody StatusRequest body,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        if (user == null || user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Municipal officer access only");
        }
        Report report = find(id);
        ReportStatus target;
        try {
            target = ReportStatus.valueOf(body.status() == null ? "" : body.status().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.error("Unknown status: " + body.status());
        }
        reportService.changeStatus(report, target, user.getName(),
                body.note() == null || body.note().isBlank() ? "Status updated via API" : body.note());
        return ApiResponse.ok("Status updated", reportMap(report));
    }

    @GetMapping("/dashboard/statistics")
    public ApiResponse statistics() {
        return ApiResponse.ok("Statistics", statsService.dashboard());
    }

    @GetMapping("/dashboard/hotspots")
    public ApiResponse hotspots() {
        return ApiResponse.ok("Hotspots", statsService.dashboard().hotspots());
    }

    @GetMapping("/dashboard/geo")
    public ApiResponse geo() {
        return ApiResponse.ok("Map points", statsService.allReportsGeo());
    }

    private Report find(String id) {
        try {
            return reportService.byPublicId(id);
        } catch (AppException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    private Map<String, Object> reportMap(Report report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("publicId", report.getPublicId());
        map.put("reporter", report.getReporter().getName());
        map.put("imageUrl", "/uploads/" + report.getImageUrl());
        map.put("afterImageUrl", report.getAfterImageUrl() == null ? null
                : "/uploads/" + report.getAfterImageUrl());
        map.put("wasteType", report.getWasteType().getLabel());
        map.put("category", report.getWasteType().name());
        map.put("confidence", report.getConfidence());
        map.put("severity", report.getSeverity().name());
        map.put("severityLabel", report.getSeverity().getLabel());
        map.put("latitude", report.getLatitude());
        map.put("longitude", report.getLongitude());
        map.put("address", report.getAddress());
        map.put("ward", report.getWard());
        map.put("description", report.getDescription());
        map.put("status", report.getStatus().name());
        map.put("statusLabel", report.getStatus().getLabel());
        map.put("createdAt", report.getCreatedAt());
        map.put("resolvedAt", report.getResolvedAt());

        Assignment assignment = reportService.activeAssignment(report);
        map.put("assignedTeam", assignment == null ? null : assignment.getTeam().getTeamName());
        return map;
    }

    private Map<String, Object> publicUser(User user) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", user.getName());
        map.put("email", user.getEmail());
        map.put("role", user.getRole().name());
        map.put("since", user.getCreatedAt());
        return map;
    }
}
