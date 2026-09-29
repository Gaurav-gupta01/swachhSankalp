package CleanVision.web;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.dto.Forms.CreateUserForm;
import CleanVision.service.AppException;
import CleanVision.repository.TeamRepository;
import CleanVision.service.AuthService;
import lombok.RequiredArgsConstructor;

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
        List<User> users = authService.allUsers();
        model.addAttribute("users", users);
        model.addAttribute("roles", List.of(Role.SUPER_ADMIN, Role.ADMIN, Role.TEAM_LEADER, Role.CITIZEN));
        model.addAttribute("teams", teamRepository.findByActiveTrueOrderByTeamNameAsc());
        return "admin/users";
    }

    @PostMapping
    public String create(@ModelAttribute("form") CreateUserForm form, RedirectAttributes redirect) {
        Role role;
        try {
            role = Role.valueOf(form.getRole() == null ? "" : form.getRole().trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", "Choose a valid role");
            return "redirect:/admin/users";
        }

        CleanVision.domain.Team team = null;
        if (role == Role.TEAM_LEADER) {
            if (form.getTeamId() == null) {
                redirect.addFlashAttribute("errorMessage", "Choose the cleanup team this leader will manage");
                return "redirect:/admin/users";
            }
            team = teamRepository.findById(form.getTeamId())
                    .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.NOT_FOUND, "Team not found"));
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
}
