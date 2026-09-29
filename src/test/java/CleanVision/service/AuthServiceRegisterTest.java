package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.Role;
import CleanVision.domain.User;
import CleanVision.dto.Forms.RegisterForm;
import CleanVision.repository.UserRepository;

@SpringBootTest
@Transactional
class AuthServiceRegisterTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository users;

    private RegisterForm form(String name, String email, String phone) {
        RegisterForm form = new RegisterForm();
        form.setName(name);
        form.setEmail(email);
        form.setPhone(phone);
        form.setPassword("secret123");
        return form;
    }

    @Test
    void registrationIsRejectedUntilTheMobileHasAnOtp() {
        RegisterForm form = form("Gaurav Gupta", "gaurav@example.com", "9876543210");

        assertThatThrownBy(() -> authService.register(form, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Verify your mobile number");

        assertThatThrownBy(() -> authService.register(form, "9999999999"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Verify your mobile number");
    }

    @Test
    void registrationSucceedsWithAProvenMobile() {
        RegisterForm form = form("Gaurav Gupta", "Gaurav@Example.com", "+91 98765 43210");

        User user = authService.register(form, "9876543210");

        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getPhone()).isEqualTo("9876543210");
        assertThat(user.getEmail()).isEqualTo("gaurav@example.com");
        assertThat(user.getRole()).isEqualTo(Role.CITIZEN);
        assertThat(user.getPhoneVerifiedAt()).isNotNull();
    }

    @Test
    void malformedEmailIsRejectedEvenWithAValidOtp() {
        RegisterForm form = form("No Mail", "not-an-email", "9876543210");

        assertThatThrownBy(() -> authService.register(form, "9876543210"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("valid email");
    }

    @Test
    void missingOrInvalidMobileIsRejectedBeforeOtp() {
        assertThatThrownBy(() -> authService.register(form("A", "a@example.com", null), "9876543210"))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> authService.register(form("A", "a@example.com", "12345"), "12345"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Indian mobile");
    }

    @Test
    void oneVerifiedMobileBelongsToOneCitizen() {
        authService.register(form("First", "first@example.com", "9876543210"), "9876543210");

        assertThatThrownBy(
                () -> authService.register(form("Second", "second@example.com", "+919876543210"),
                        "9876543210"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("already linked to a citizen account");
    }

    @Test
    void staffAccountsCreatedByOfficersAreImmediatelyUsable() {
        User officer = authService.addUser("Officer", "officer@example.com", "9822000001",
                "secret123", Role.ADMIN);

        assertThat(officer.isPhoneVerified()).isTrue();
        assertThat(authService.needsMobileVerification(officer)).isFalse();
    }

    @Test
    void aCitizenWhoNeverVerifiedIsAskedToVerifyAtLogin() {
        User citizen = new User();
        citizen.setName("Legacy Citizen");
        citizen.setEmail("legacy@example.com");
        citizen.setPhone("9822000002");
        citizen.setRole(Role.CITIZEN);
        citizen.setPasswordHash("x");
        users.save(citizen);

        assertThat(citizen.isPhoneVerified()).isFalse();
        assertThat(authService.needsMobileVerification(citizen)).isTrue();
    }
}
