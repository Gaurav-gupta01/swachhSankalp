package CleanVision.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.dto.Forms.LoginForm;
import CleanVision.dto.Forms.RegisterForm;
import CleanVision.repository.UserRepository;
import CleanVision.security.PasswordHasher;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordHasher hasher;

    @Transactional
    public User register(RegisterForm form) {
        return addUser(form.getName(), form.getEmail(), form.getPhone(), form.getPassword(), Role.CITIZEN);
    }

    @Transactional
    public User addUser(String name, String email, String phone, String rawPassword, Role role) {
        return addUser(name, email, phone, rawPassword, role, null);
    }

    @Transactional
    public User addUser(String name, String email, String phone, String rawPassword, Role role,
            CleanVision.domain.Team team) {
        if (role == Role.TEAM_LEADER && team == null) {
            throw new AppException("A team leader must be linked to a cleanup team");
        }
        if (name == null || name.isBlank()) {
            throw new AppException("Name is required");
        }
        if (email == null || email.isBlank() || !email.contains("@")) {
            throw new AppException("Enter a valid email");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new AppException("Password must be at least 6 characters");
        }

        String normalized = email.trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new AppException("An account with this email already exists");
        }

        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalized);
        user.setPhone(phone == null ? null : phone.trim());
        user.setPasswordHash(hasher.hash(rawPassword));
        user.setRole(role == null ? Role.CITIZEN : role);
        user.setTeam(role == Role.TEAM_LEADER ? team : null);
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public java.util.List<User> allUsers() {
        return users.findAll(org.springframework.data.domain.Sort.by("createdAt").descending());
    }

    @Transactional(readOnly = true)
    public User authenticate(LoginForm form) {
        return users.findByEmailIgnoreCase(form.getEmail().trim())
                .filter(user -> hasher.matches(form.getPassword(), user.getPasswordHash()))
                .orElseThrow(() -> new AppException("Invalid email or password"));
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return users.findById(id).orElseThrow(() -> new AppException("Account not found"));
    }
}
