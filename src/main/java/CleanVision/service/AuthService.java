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
        String email = form.getEmail().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new AppException("An account with this email already exists");
        }
        if (form.getPassword().length() < 6) {
            throw new AppException("Password must be at least 6 characters");
        }

        User user = new User();
        user.setName(form.getName().trim());
        user.setEmail(email);
        user.setPhone(form.getPhone() == null ? null : form.getPhone().trim());
        user.setPasswordHash(hasher.hash(form.getPassword()));
        user.setRole(Role.CITIZEN);
        return users.save(user);
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

    @Transactional
    public void promoteToAdmin(String email) {
        users.findByEmailIgnoreCase(email).ifPresent(user -> user.setRole(Role.ADMIN));
    }
}
