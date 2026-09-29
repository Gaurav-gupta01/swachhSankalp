package CleanVision.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import CleanVision.domain.OtpCode;
import CleanVision.repository.OtpCodeRepository;
import CleanVision.sms.OtpSender;

@SpringBootTest
class OtpServiceTest {

    /** Stands in for MSG91 and remembers the last code it was asked to deliver. */
    static class RecordingSender implements OtpSender {
        final List<String> codes = new ArrayList<>();

        @Override
        public boolean send(String mobile, String code, int expiryMinutes) {
            codes.add(code);
            return true;
        }

        String last() {
            return codes.get(codes.size() - 1);
        }
    }

    @Autowired
    private OtpCodeRepository repository;

    private RecordingSender sender;
    private OtpService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        sender = new RecordingSender();
        service = new OtpService(repository,
                new OtpProperties(5, 5, 60, 10), sender, sender);
    }

    @Test
    void validIndianMobileFormatsAreNormalised() {
        assertThat(OtpService.normalize("9876543210")).isEqualTo("9876543210");
        assertThat(OtpService.normalize("+91 98765 43210")).isEqualTo("9876543210");
        assertThat(OtpService.normalize("91-98765-43210")).isEqualTo("9876543210");
        assertThat(OtpService.normalize("09876543210")).isEqualTo("9876543210");
    }

    @Test
    void invalidMobilesAreRejected() {
        assertThatThrownBy(() -> OtpService.normalize("12345")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> OtpService.normalize("5876543210")).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> OtpService.normalize(null)).isInstanceOf(AppException.class);
        assertThat(OtpService.isValidMobile("5876543210")).isFalse();
        assertThat(OtpService.isValidMobile("9876543210")).isTrue();
    }

    @Test
    void issuedCodeIsVerifiedAndCannotBeReused() {
        service.send("9876543210", OtpCode.PURPOSE_REGISTER);

        assertThat(sender.codes).hasSize(1);
        assertThat(sender.last()).hasSize(6);
        assertThat(service.verify("9876543210", OtpCode.PURPOSE_REGISTER, sender.last())).isTrue();

        assertThatThrownBy(
                () -> service.verify("9876543210", OtpCode.PURPOSE_REGISTER, sender.last()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void wrongCodeBurnsAnAttemptAndEventuallyLocksTheCode() {
        service.send("9876543210", OtpCode.PURPOSE_REGISTER);
        String wrong = sender.last().equals("000000") ? "111111" : "000000";

        for (int attempt = 1; attempt <= 5; attempt++) {
            final String candidate = wrong;
            assertThatThrownBy(
                    () -> service.verify("9876543210", OtpCode.PURPOSE_REGISTER, candidate))
                    .isInstanceOf(AppException.class);
        }

        // Even the right code is refused once the attempts budget is gone.
        assertThatThrownBy(
                () -> service.verify("9876543210", OtpCode.PURPOSE_REGISTER, sender.last()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void expiredCodeIsRejected() {
        service.send("9876543210", OtpCode.PURPOSE_REGISTER);
        OtpCode stored = repository.findTopByMobileAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
                "9876543210", OtpCode.PURPOSE_REGISTER).orElseThrow();
        stored.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        repository.save(stored);

        assertThatThrownBy(
                () -> service.verify("9876543210", OtpCode.PURPOSE_REGISTER, sender.last()))
                .isInstanceOf(AppException.class);
    }

    @Test
    void resendIsBlockedDuringTheCooldownWindow() {
        service.send("9876543210", OtpCode.PURPOSE_REGISTER);

        assertThatThrownBy(() -> service.send("9876543210", OtpCode.PURPOSE_REGISTER))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("seconds before requesting another code");
    }

    @Test
    void hourlySendLimitIsEnforced() {
        AtomicInteger clock = new AtomicInteger();
        OtpService generousCooldown = new OtpService(repository,
                new OtpProperties(5, 5, 0, 3), sender, sender);

        // Cooldown of zero lets three codes out; the fourth is stopped by the hourly cap.
        while (clock.incrementAndGet() <= 3) {
            generousCooldown.send("9876543210", OtpCode.PURPOSE_REGISTER);
        }
        assertThatThrownBy(() -> generousCooldown.send("9876543210", OtpCode.PURPOSE_REGISTER))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test
    void codesForDifferentPurposesAreIndependent() {
        service.send("9876543210", OtpCode.PURPOSE_REGISTER);
        String registerCode = sender.last();

        OtpService loginService = new OtpService(repository,
                new OtpProperties(5, 5, 0, 10), sender, sender);
        loginService.send("9876543210", OtpCode.PURPOSE_LOGIN);

        assertThat(loginService.verify("9876543210", OtpCode.PURPOSE_LOGIN, sender.last())).isTrue();
        assertThat(service.verify("9876543210", OtpCode.PURPOSE_REGISTER, registerCode)).isTrue();
    }
}
