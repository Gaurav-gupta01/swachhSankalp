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
    void registrationNormalisesTheMobileAndCreatesTheAccount() {
        RegisterForm form = form("Gaurav Gupta", "Gaurav@Example.com", "+91 98765 43210");

        User user = authService.register(form);

        assertThat(user.isPhoneVerified()).isTrue();
        assertThat(user.getPhone()).isEqualTo("9876543210");
        assertThat(user.getEmail()).isEqualTo("gaurav@example.com");
        assertThat(user.getRole()).isEqualTo(Role.CITIZEN);
        assertThat(user.getPhoneVerifiedAt()).isNotNull();
    }

    @Test
    void malformedEmailIsRejected() {
        RegisterForm form = form("No Mail", "not-an-email", "9876543210");

        assertThatThrownBy(() -> authService.register(form))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("valid email");
    }

    @Test
    void missingOrInvalidMobileIsRejected() {
        assertThatThrownBy(() -> authService.register(form("A", "a@example.com", null)))
                .isInstanceOf(AppException.class);

        assertThatThrownBy(() -> authService.register(form("A", "a@example.com", "12345")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Indian mobile");
    }

    @Test
    void missingNameIsRejected() {
        assertThatThrownBy(() -> authService.register(form("  ", "a@example.com", "9876543210")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Name is required");
    }

    @Test
    void oneMobileBelongsToOneCitizen() {
        authService.register(form("First", "first@example.com", "9876543210"));

        assertThatThrownBy(() -> authService.register(form("Second", "second@example.com", "+919876543210")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("already linked to a citizen account");
    }

    @Test
    void oneEmailBelongsToOneAccount() {
        authService.register(form("First", "taken@example.com", "9876543210"));

        assertThatThrownBy(() -> authService.register(form("Second", "Taken@Example.com", "9876543211")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("email already exists");
    }

    @Test
    void staffAccountsCreatedByOfficersAreImmediatelyUsable() {
        User officer = authService.addUser("Officer", "officer@example.com", "9822000001",
                "secret123", Role.ADMIN);

        assertThat(officer.isPhoneVerified()).isTrue();
        assertThat(users.existsByEmailIgnoreCase("officer@example.com")).isTrue();
    }
}
