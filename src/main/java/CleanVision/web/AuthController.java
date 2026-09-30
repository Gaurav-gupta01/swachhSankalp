package CleanVision.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.User;
import CleanVision.dto.Forms.LoginForm;
import CleanVision.dto.Forms.RegisterForm;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

/**
 * Login, logout and citizen registration. Mobile OTP verification has been switched
 * off: an account is created as soon as the form is valid, and every password check
 * goes straight to the dashboard.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage", "Welcome back, " + user.getName());
            return safeRedirect(next, user);
        } catch (AppException ex) {
            redirect.addFlashAttribute("form", form);
            return "redirect:/login?error=1" + (next == null ? "" : "&next=" + next);
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegisterForm form,
            HttpServletRequest request,
            RedirectAttributes redirect) {
        try {
            User user = authService.register(form, form.getPhone());
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage",
                    "Account created. Report waste the moment you see it.");
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

    private void startSession(HttpServletRequest request, User user) {
        HttpSession session = request.getSession(true);
        session.setAttribute(AuthInterceptor.SESSION_USER_ID, user.getId());
    }

    /** Role-safe redirect — a citizen can never be bounced into /admin. */
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
}
