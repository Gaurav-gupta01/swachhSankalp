package CleanVision.web;

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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

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
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/login" + (next == null ? "" : "?next=" + next);
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
            User user = authService.register(form);
            startSession(request, user);
            redirect.addFlashAttribute("flashMessage",
                    "Account created. Report waste the moment you see it.");
            return "redirect:/";
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

    private String safeRedirect(String next, User user) {
        if (next != null && next.startsWith("/") && !next.startsWith("//")) {
            if (next.startsWith("/admin") && !user.getRole().isOfficer()) {
                return "redirect:/";
            }
            if (next.startsWith("/team") && !user.getRole().isTeamLeader()) {
                return "redirect:/";
            }
            return "redirect:" + next;
        }
        if (user.getRole().isOfficer()) {
            return "redirect:/admin";
        }
        return user.getRole().isTeamLeader() ? "redirect:/team" : "redirect:/";
    }
}
