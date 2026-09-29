package CleanVision.service;

import java.time.LocalDateTime;
import java.util.List;
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
    private final PasswordHasher hasher;

    /**
     * Citizen self-registration. The mobile must already have been proven with an OTP
     * during this session ({@code verifiedMobile}); the email only has to be well formed.
     */
    @Transactional
    public User register(RegisterForm form, String verifiedMobile) {
        String phone = OtpService.normalize(form.getPhone());
        String proven = verifiedMobile == null ? null : OtpService.normalize(verifiedMobile);
        if (proven == null || !phone.equals(proven)) {
            throw new AppException("Verify your mobile number with the OTP before creating the account");
        }
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
     * Creates an account from the admin screens. Staff (and any account an officer
     * creates) is considered mobile-verified — only citizen self-registration runs OTP.
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

    /** Records that the citizen proved this number with an OTP. */
    @Transactional
    public User markMobileVerified(User user, String rawPhone) {
        String phone = OtpService.normalize(rawPhone);
        user.setPhone(phone);
        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(LocalDateTime.now());
        return users.save(user);
    }

    /** Citizens cannot sign in until their mobile is proven. */
    public boolean needsMobileVerification(User user) {
        return user != null && user.getRole() == Role.CITIZEN && !user.isPhoneVerified();
    }

    /**
     * Finds the unverified citizen whose (possibly legacy formatted) number matches, so an
     * existing account can complete verification after logging in. Legacy databases hold
     * several accounts on one demo number, so this never assumes a unique match.
     */
    @Transactional(readOnly = true)
    public User findByMobile(String rawPhone) {
        String phone = OtpService.normalize(rawPhone);
        java.util.List<User> candidates = new java.util.ArrayList<>(users.findByPhone(phone));
        if (candidates.isEmpty()) {
            users.findAll().stream()
                    .filter(u -> u.getPhone() != null && OtpService.isValidMobile(u.getPhone())
                            && phone.equals(OtpService.normalize(u.getPhone())))
                    .forEach(candidates::add);
        }
        return candidates.stream()
                .filter(u -> u.getRole() == Role.CITIZEN && !u.isPhoneVerified())
                .findFirst()
                .orElse(null);
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
