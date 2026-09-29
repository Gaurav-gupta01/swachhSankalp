package CleanVision.web;

import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.dto.Forms.LoginForm;
import CleanVision.dto.Forms.RegisterForm;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.AuthService;
import CleanVision.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AuthController {

    /** Session keys for the two OTP gated flows. */
    public static final String VERIFIED_PHONE = "cvVerifiedPhone";
    public static final String PENDING_VERIFY_USER_ID = "cvPendingVerifyUserId";
    public static final String PENDING_VERIFY_PHONE = "cvPendingVerifyPhone";
    public static final String PENDING_VERIFY_NEXT = "cvPendingVerifyNext";

    private final AuthService authService;
    private final OtpService otpService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String next,
            @RequestParam(required = false) String error,
            Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new LoginForm());
        }
        model.addAttribute("next", next);
        model.addAttribute("error", error != null);
        return "login";
    }

    @PostMapping("/login")
    public String login(@ModelAttribute("form") LoginForm form,
            @RequestParam(required = false) String next,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        try {
            User user = authService.authenticate(form);
            if (authService.needsMobileVerification(user)) {
                if (user.getPhone() == null || user.getPhone().isBlank()) {
                    redirect.addFlashAttribute("errorMessage",
                            "No mobile number is on file for this account. Ask an officer to add one, then log in again.");
                    return "redirect:/login" + (next == null ? "" : "?next=" + next);
                }
                return beginMobileVerification(request, user, next);
            }
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage", "Welcome back, " + user.getName());
            return safeRedirect(next, user);
        } catch (AppException ex) {
            redirect.addFlashAttribute("form", form);
            return "redirect:/login?error=1" + (next == null ? "" : "&next=" + next);
        }
    }

    /**
     * Existing citizen whose mobile was never proven: password was correct, but the
     * account stays locked until the OTP confirms the number on file.
     */
    private String beginMobileVerification(HttpServletRequest request, User user, String next) {
        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_VERIFY_USER_ID, user.getId());
        session.setAttribute(PENDING_VERIFY_PHONE, user.getPhone());
        session.setAttribute(PENDING_VERIFY_NEXT, next);
        session.removeAttribute(AuthInterceptor.SESSION_USER_ID);
        return "redirect:/verify-mobile" + (next == null ? "" : "?next=" + next);
    }

    @GetMapping("/verify-mobile")
    public String verifyMobilePage(@RequestParam(required = false) String next,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            Model model) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(PENDING_VERIFY_USER_ID);
        String phone = session == null ? null : (String) session.getAttribute(PENDING_VERIFY_PHONE);
        if (!(userId instanceof Long) || phone == null) {
            return "redirect:/login";
        }
        model.addAttribute("mobile", phone);
        model.addAttribute("mobileMasked", mask(phone));
        model.addAttribute("purpose", CleanVision.domain.OtpCode.PURPOSE_LOGIN);
        model.addAttribute("next", next);
        model.addAttribute("hasError", error != null);
        return "verify-mobile";
    }

    /** Plain-form fallback so verification still works with JavaScript disabled. */
    @PostMapping("/verify-mobile/resend")
    public String resendVerificationCode(HttpServletRequest request, RedirectAttributes redirect) {
        HttpSession session = request.getSession(false);
        String phone = session == null ? null : (String) session.getAttribute(PENDING_VERIFY_PHONE);
        if (phone == null) {
            return "redirect:/login";
        }
        try {
            otpService.send(phone, CleanVision.domain.OtpCode.PURPOSE_LOGIN);
            redirect.addFlashAttribute("flashMessage", "New code sent to " + mask(phone));
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/verify-mobile";
    }

    @PostMapping("/verify-mobile")
    public String verifyMobile(@RequestParam String otp,
            @RequestParam(required = false) String next,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        HttpSession session = request.getSession(false);
        Object userId = session == null ? null : session.getAttribute(PENDING_VERIFY_USER_ID);
        String phone = session == null ? null : (String) session.getAttribute(PENDING_VERIFY_PHONE);
        if (!(userId instanceof Long) || phone == null) {
            return "redirect:/login";
        }
        try {
            otpService.verify(phone, CleanVision.domain.OtpCode.PURPOSE_LOGIN, otp);
            User user = authService.markMobileVerified(authService.findById((Long) userId), phone);
            session.removeAttribute(PENDING_VERIFY_USER_ID);
            session.removeAttribute(PENDING_VERIFY_PHONE);
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage",
                    "Mobile number verified — welcome, " + user.getName());
            return safeRedirect(next, user);
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/verify-mobile" + (next == null ? "" : "?next=" + next);
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        model.addAttribute("purpose", CleanVision.domain.OtpCode.PURPOSE_REGISTER);
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegisterForm form,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        try {
            User user = authService.register(form, verifiedPhone(request));
            HttpSession session = request.getSession(true);
            session.removeAttribute(VERIFIED_PHONE);
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage",
                    "Account created and mobile verified. Report waste the moment you see it.");
            return "redirect:" + user.getRole().dashboardPath();
        } catch (AppException ex) {
            redirect.addFlashAttribute("form", form);
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/register";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/login";
    }

    /** The OTP proven during this session, if any. */
    public static String verifiedPhone(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(VERIFIED_PHONE);
    }

    private void startSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
        session.removeAttribute(PENDING_VERIFY_USER_ID);
        session.removeAttribute(PENDING_VERIFY_PHONE);
        session.removeAttribute(PENDING_VERIFY_NEXT);
    }

    private String safeRedirect(String next, User user) {
        String dashboard = user.getRole().dashboardPath();
        if (next != null && next.startsWith("/") && !next.startsWith("//") && !next.equals("/")) {
            if (next.startsWith("/admin") && !user.getRole().isOfficer()) {
                return "redirect:" + dashboard;
            }
            if (next.startsWith("/team") && !user.getRole().isTeamLeader()) {
                return "redirect:" + dashboard;
            }
            if (next.startsWith("/my-reports") && !user.getRole().canReport()) {
                return "redirect:" + dashboard;
            }
            return "redirect:" + next;
        }
        return "redirect:" + dashboard;
    }

    private String nextParam(String next) {
        return next == null ? "/my-reports" : next;
    }

    private String mask(String phone) {
        if (phone == null || phone.length() < 4) {
            return phone;
        }
        return "******" + phone.substring(phone.length() - 4);
    }

    /** Exposed for the OTP endpoints so API errors read the same as the UI. */
    static Map<String, Object> verificationRequired(User user) {
        return Map.of("requiresMobileVerification", true,
                "mobile", user.getPhone() == null ? "" : maskStatic(user.getPhone()));
    }

    private static String maskStatic(String phone) {
        return phone.length() < 4 ? phone : "******" + phone.substring(phone.length() - 4);
    }
}
