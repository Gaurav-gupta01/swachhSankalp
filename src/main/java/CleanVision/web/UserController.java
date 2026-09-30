package CleanVision.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.Role;
import CleanVision.domain.Team;
import CleanVision.domain.User;
import CleanVision.dto.Forms.CreateUserForm;
import CleanVision.repository.TeamRepository;
import CleanVision.security.AuthInterceptor;
import CleanVision.service.AppException;
import CleanVision.service.AuthService;
import lombok.RequiredArgsConstructor;

/**
 * Super admin user management: list, create, edit and delete the accounts that can sign
 * in. The interceptor already limits everything under {@code /admin/users} to the super
 * admin role.
 */
@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final TeamRepository teamRepository;

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("form")) {
            CreateUserForm form = new CreateUserForm();
            form.setRole(Role.ADMIN.name());
            model.addAttribute("form", form);
        }
        model.addAttribute("users", authService.allUsers());
        addOptions(model);
        return "admin/users";
    }

    @PostMapping
    public String create(@ModelAttribute("form") CreateUserForm form, RedirectAttributes redirect) {
        Role role = parseRole(form.getRole());
        if (role == null) {
            redirect.addFlashAttribute("errorMessage", "Choose a valid role");
            return "redirect:/admin/users";
        }
        Team team;
        try {
            team = resolveTeam(role, form.getTeamId());
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/users";
        }

        try {
            User created = authService.addUser(form.getName(), form.getEmail(), form.getPhone(),
                    form.getPassword(), role, team, true);
            redirect.addFlashAttribute("flashMessage",
                    created.getName() + " created as " + role.getLabel());
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        User user = authService.findById(id);
        if (!model.containsAttribute("form")) {
            CreateUserForm form = new CreateUserForm();
            form.setName(user.getName());
            form.setEmail(user.getEmail());
            form.setPhone(user.getPhone());
            form.setRole(user.getRole().name());
            form.setTeamId(user.getTeam() == null ? null : user.getTeam().getId());
            model.addAttribute("form", form);
        }
        model.addAttribute("user", user);
        model.addAttribute("editing", true);
        addOptions(model);
        return "admin/user-form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
            @ModelAttribute("form") CreateUserForm form,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User actor,
            RedirectAttributes redirect) {
        Role role = parseRole(form.getRole());
        if (role == null) {
            redirect.addFlashAttribute("errorMessage", "Choose a valid role");
            return "redirect:/admin/users/" + id + "/edit";
        }
        Team team;
        try {
            team = resolveTeam(role, form.getTeamId());
            authService.updateUser(id, form.getName(), form.getEmail(), form.getPhone(),
                    form.getPassword(), role, team);
            redirect.addFlashAttribute("flashMessage", "Account updated");
            return "redirect:/admin/users";
        } catch (AppException ex) {
            redirect.addFlashAttribute("form", form);
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/admin/users/" + id + "/edit";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
            @RequestAttribute(name = AuthInterceptor.CURRENT_USER, required = false) User actor,
            RedirectAttributes redirect) {
        try {
            User doomed = authService.findById(id);
            authService.deleteUser(id, actor);
            redirect.addFlashAttribute("flashMessage", doomed.getName() + " deleted");
        } catch (AppException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    private Role parseRole(String value) {
        try {
            return Role.valueOf(value == null ? "" : value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Team resolveTeam(Role role, Long teamId) {
        if (role != Role.TEAM_LEADER) {
            return null;
        }
        if (teamId == null) {
            throw new AppException("Choose the cleanup team this leader will manage");
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> new AppException("That cleanup team no longer exists"));
    }

    private void addOptions(Model model) {
        model.addAttribute("roles", List.of(Role.SUPER_ADMIN, Role.ADMIN, Role.TEAM_LEADER, Role.CITIZEN));
        model.addAttribute("teams", teamRepository.findByActiveTrueOrderByTeamNameAsc());
    }
}
