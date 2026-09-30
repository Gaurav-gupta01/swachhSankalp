package CleanVision.service;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

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

    /** Deliberately stricter than @Email: requires a domain with a dot. */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");

    private final UserRepository users;
    private final CleanVision.repository.ReportRepository reports;
    private final PasswordHasher hasher;

    /**
     * Citizen self-registration: the mobile is normalised and must not already belong to
     * another citizen, the email only has to be well formed.
     */
    @Transactional
    public User register(RegisterForm form) {
        String phone = Phone.normalize(form.getPhone());
        if (users.existsByRoleAndPhoneAndPhoneVerifiedTrue(Role.CITIZEN, phone)) {
            throw new AppException("This mobile number is already linked to a citizen account");
        }
        return addUser(form.getName(), form.getEmail(), phone, form.getPassword(), Role.CITIZEN, null, true);
    }

    @Transactional
    public User addUser(String name, String email, String phone, String rawPassword, Role role) {
        return addUser(name, email, phone, rawPassword, role, null, true);
    }

    /**
     * Creates an account from the admin screens or the officer API. Any account created
     * this way is treated as mobile-verified.
     */
    @Transactional
    public User addUser(String name, String email, String phone, String rawPassword, Role role,
            CleanVision.domain.Team team, boolean mobileVerified) {
        if (role == Role.TEAM_LEADER && team == null) {
            throw new AppException("A team leader must be linked to a cleanup team");
        }
        if (name == null || name.isBlank()) {
            throw new AppException("Name is required");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new AppException("Enter a valid email address");
        }
        if (rawPassword == null || rawPassword.length() < 6) {
            throw new AppException("Password must be at least 6 characters");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new AppException("An account with this email already exists");
        }

        String mobile = phone == null || phone.isBlank() ? null : phone.trim();

        User user = new User();
        user.setName(name.trim());
        user.setEmail(normalized);
        user.setPhone(mobile);
        user.setPasswordHash(hasher.hash(rawPassword));
        user.setRole(role == null ? Role.CITIZEN : role);
        user.setTeam(role == Role.TEAM_LEADER ? team : null);
        user.setPhoneVerified(mobileVerified && mobile != null);
        if (user.isPhoneVerified()) {
            user.setPhoneVerifiedAt(LocalDateTime.now());
        }
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

    /**
     * Super admin edits an account in place. An empty password keeps the existing one, so
     * a profile change never locks anybody out, and a team leader always keeps a team.
     */
    @Transactional
    public User updateUser(Long id, String name, String email, String phone, String password,
            Role role, CleanVision.domain.Team team) {
        User user = findById(id);
        if (name == null || name.isBlank()) {
            throw new AppException("Name is required");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new AppException("Enter a valid email address");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        User existing = users.findByEmailIgnoreCase(normalized).orElse(null);
        if (existing != null && !existing.getId().equals(user.getId())) {
            throw new AppException("An account with this email already exists");
        }
        if (role == Role.TEAM_LEADER && team == null) {
            throw new AppException("A team leader must be linked to a cleanup team");
        }

        String mobile = phone == null || phone.isBlank() ? null : Phone.normalize(phone);
        if (mobile != null && role == Role.CITIZEN) {
            boolean takenByAnother = users.findByPhone(mobile).stream()
                    .anyMatch(other -> !other.getId().equals(user.getId())
                            && other.getRole() == Role.CITIZEN && other.isPhoneVerified());
            if (takenByAnother) {
                throw new AppException("This mobile number is already linked to a citizen account");
            }
        }

        user.setName(name.trim());
        user.setEmail(normalized);
        user.setPhone(mobile);
        user.setRole(role);
        user.setTeam(role == Role.TEAM_LEADER ? team : null);
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new AppException("Password must be at least 6 characters");
            }
            user.setPasswordHash(hasher.hash(password));
        }
        return users.save(user);
    }

    /**
     * Removes an account. The signed-in super admin cannot delete themselves, the last
     * super admin is protected, and accounts that own reports are kept so history stays
     * attached to the reporter.
     */
    @Transactional
    public void deleteUser(Long id, User actor) {
        if (actor != null && actor.getId() != null && actor.getId().equals(id)) {
            throw new AppException("You cannot delete the account you are signed in with");
        }
        User user = findById(id);
        if (user.getRole() == Role.SUPER_ADMIN && users.countByRole(Role.SUPER_ADMIN) <= 1) {
            throw new AppException("At least one super admin account must remain");
        }
        if (reports.countByReporterId(id) > 0) {
            throw new AppException(user.getName() + " has filed reports, so the account cannot be "
                    + "deleted — their reports must keep a reporter");
        }
        users.delete(user);
    }
}
