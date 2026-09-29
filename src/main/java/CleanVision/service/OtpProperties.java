package CleanVision.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/** Tunables for mobile OTP issuance. Overridable through application properties. */
@Component
@Getter
public class OtpProperties {

    private final int ttlMinutes;
    private final int maxAttempts;
    private final int resendCooldownSeconds;
    private final int maxSendsPerHour;

    public OtpProperties(
            @Value("${cleanvision.otp.ttl-minutes:5}") int ttlMinutes,
            @Value("${cleanvision.otp.max-attempts:5}") int maxAttempts,
            @Value("${cleanvision.otp.resend-cooldown-seconds:60}") int resendCooldownSeconds,
            @Value("${cleanvision.otp.max-sends-per-hour:5}") int maxSendsPerHour) {
        this.ttlMinutes = ttlMinutes;
        this.maxAttempts = maxAttempts;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.maxSendsPerHour = maxSendsPerHour;
    }
}
