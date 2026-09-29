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
import CleanVision.domain.OtpCode;
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
import CleanVision.service.OtpService;
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
    private final OtpService otpService;

    public record StatusRequest(String status, String note) {
    }

    public record OtpRequest(String phone, String code, String purpose) {
    }

    @PostMapping("/auth/register")
    public ApiResponse register(@RequestBody RegisterForm form,
            jakarta.servlet.http.HttpServletRequest request, HttpSession session) {
        try {
            User user = authService.register(form, AuthController.verifiedPhone(request));
            session.removeAttribute(AuthController.VERIFIED_PHONE);
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
            if (authService.needsMobileVerification(user)) {
                return ApiResponse.error(
                        "Mobile number is not verified yet. Send and verify an OTP first.",
                        AuthController.verificationRequired(user));
            }
            session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
            return ApiResponse.ok("Logged in", publicUser(user));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    /** Step 1 — deliver a 6-digit code to the number (MSG91, log fallback if unavailable). */
    @PostMapping("/auth/otp/send")
    public ApiResponse sendOtp(@RequestBody OtpRequest body) {
        try {
            String purpose = normalizePurpose(body.purpose());
            if (OtpCode.PURPOSE_LOGIN.equals(purpose)) {
                User user = authService.findByMobile(body.phone());
                if (user == null || !authService.needsMobileVerification(user)) {
                    return ApiResponse.error("No unverified citizen account uses this number");
                }
            }
            otpService.send(body.phone(), purpose);
            return ApiResponse.ok("OTP sent", Map.of("purpose", purpose));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    /** Step 2 — check the code; a verified number is stored for registration. */
    @PostMapping("/auth/otp/verify")
    public ApiResponse verifyOtp(@RequestBody OtpRequest body, HttpSession session) {
        try {
            String purpose = normalizePurpose(body.purpose());
            otpService.verify(body.phone(), purpose, body.code());
            if (OtpCode.PURPOSE_LOGIN.equals(purpose)) {
                User user = authService.findByMobile(body.phone());
                if (user != null && authService.needsMobileVerification(user)) {
                    authService.markMobileVerified(user, body.phone());
                }
                Map<String, Object> data = new LinkedHashMap<>();
                if (user != null) {
                    data.putAll(publicUser(user));
                }
                // A browser sitting on the login gate is let straight in.
                Object pending = session.getAttribute(AuthController.PENDING_VERIFY_USER_ID);
                if (user != null && pending instanceof Long id && id.equals(user.getId())) {
                    session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
                    data.put("redirect", landing(user, session.getAttribute(AuthController.PENDING_VERIFY_NEXT)));
                    session.removeAttribute(AuthController.PENDING_VERIFY_USER_ID);
                    session.removeAttribute(AuthController.PENDING_VERIFY_PHONE);
                    session.removeAttribute(AuthController.PENDING_VERIFY_NEXT);
                }
                return ApiResponse.ok("Mobile verified", data);
            }
            session.setAttribute(AuthController.VERIFIED_PHONE, OtpService.normalize(body.phone()));
            return ApiResponse.ok("Mobile verified", Map.of("phone", OtpService.normalize(body.phone())));
        } catch (AppException ex) {
            return ApiResponse.error(ex.getMessage());
        }
    }

    private String normalizePurpose(String purpose) {
        if (purpose == null || purpose.isBlank()) {
            return OtpCode.PURPOSE_REGISTER;
        }
        String value = purpose.trim().toUpperCase();
        if (!value.equals(OtpCode.PURPOSE_REGISTER) && !value.equals(OtpCode.PURPOSE_LOGIN)) {
            throw new AppException("Unknown OTP purpose: " + purpose);
        }
        return value;
    }

    /** Role-safe landing page — a citizen can never be bounced into /admin. */
    private String landing(User user, Object nextValue) {
        String dashboard = user.getRole().dashboardPath();
        if (!(nextValue instanceof String next) || !next.startsWith("/")
                || next.startsWith("//") || next.equals("/")) {
            return dashboard;
        }
        if (next.startsWith("/admin") && !user.getRole().isOfficer()) {
            return dashboard;
        }
        if (next.startsWith("/team") && !user.getRole().isTeamLeader()) {
            return dashboard;
        }
        if (next.startsWith("/my-reports") && !user.getRole().canReport()) {
            return dashboard;
        }
        return next;
    }

    @PostMapping("/ai/classify")
    public AiClassifyResponse classify(@RequestParam("image") MultipartFile image,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user,
            HttpSession session) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
        }
        if (!user.getRole().canReport()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only citizen accounts can submit reports");
        }
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
        if (!user.getRole().canReport()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only citizen accounts can submit reports");
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
        List<Map<String, Object>> data = (user.getRole().isOfficer() ? reportService.all()
                : reportService.byUser(user))
                .stream().map(this::reportMap).toList();
        return ApiResponse.ok("Reports", data);
    }

    @GetMapping("/reports/{id}")
    public ApiResponse getReport(@PathVariable String id,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User user) {
        Report report = find(id);
        if (user == null || (!user.getRole().isOfficer() && !user.getId().equals(report.getReporter().getId()))) {
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
        if (user == null || !user.getRole().isOfficer()) {
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
        map.put("reporterEmail", report.getReporter().getEmail());
        map.put("reporterPhone", report.getReporter().getPhone());
        map.put("imageUrl", report.getImageUrl() == null ? null : "/uploads/" + report.getImageUrl());
        map.put("afterImageUrl", report.getAfterImageUrl() == null ? null
                : "/uploads/" + report.getAfterImageUrl());
        map.put("wasteType", report.getWasteType().getLabel());
        map.put("category", report.getWasteType().name());
        map.put("animalType", report.getAnimalType() == null ? null : report.getAnimalType().name());
        map.put("animalTypeLabel", report.getAnimalType() == null ? null : report.getAnimalType().getLabel());
        map.put("animalCondition", report.getAnimalCondition() == null ? null : report.getAnimalCondition().name());
        map.put("animalConditionLabel",
                report.getAnimalCondition() == null ? null : report.getAnimalCondition().getLabel());
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
