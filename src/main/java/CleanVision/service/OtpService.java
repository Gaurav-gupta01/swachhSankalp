package CleanVision.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import CleanVision.domain.OtpCode;
import CleanVision.repository.OtpCodeRepository;
import CleanVision.sms.Msg91OtpSender;
import CleanVision.sms.OtpSender;
import lombok.extern.slf4j.Slf4j;

/**
 * Issues and checks mobile OTPs.
 *
 * <p>Rules enforced here (they are the same for the HTML form and the JSON API):
 * Indian mobile format, 6 digits, 5-minute expiry, 5 attempts, 60-second resend
 * cooldown and at most 5 sends per number per hour. The code is stored hashed and is
 * verified locally — MSG91 is only a transport.</p>
 */
@Service
@Slf4j
public class OtpService {

    private static final int CODE_LENGTH = 6;

    private final OtpCodeRepository codes;
    private final OtpProperties properties;
    private final OtpSender msg91;
    private final OtpSender logging;

    public OtpService(OtpCodeRepository codes,
            OtpProperties properties,
            @Qualifier("msg91OtpSender") OtpSender msg91,
            @Qualifier("loggingOtpSender") OtpSender logging) {
        this.codes = codes;
        this.properties = properties;
        this.msg91 = msg91;
        this.logging = logging;
    }

    /**
     * Normalises an Indian mobile number to its 10 digits.
     * Accepts {@code 9876543210}, {@code +91 98765 43210}, {@code 919876543210}, {@code 09876543210}.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AppException("Mobile number is required");
        }
        String digits = raw.trim().replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        } else if (digits.length() == 11 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 10 || !digits.matches("^[6-9][0-9]{9}$")) {
            throw new AppException("Enter a valid 10-digit Indian mobile number");
        }
        return digits;
    }

    public static boolean isValidMobile(String raw) {
        try {
            normalize(raw);
            return true;
        } catch (AppException ex) {
            return false;
        }
    }

    /** Generates a fresh code, stores its hash and hands it to the SMS gateway. */
    @Transactional
    public void send(String rawMobile, String purpose) {
        String mobile = normalize(rawMobile);
        purgeExpired();

        LocalDateTime now = LocalDateTime.now();
        long recentSends = codes.countByMobileAndPurposeAndCreatedAtAfter(
                mobile, purpose, now.minusHours(1));
        if (recentSends >= properties.getMaxSendsPerHour()) {
            throw new AppException("Too many OTP requests for this number. Try again later.");
        }

        List<OtpCode> active = codes.findByMobileAndPurposeAndCreatedAtAfter(
                mobile, purpose, now.minusSeconds(properties.getResendCooldownSeconds()));
        if (!active.isEmpty()) {
            OtpCode latest = active.get(active.size() - 1);
            long waitSeconds = properties.getResendCooldownSeconds()
                    - java.time.Duration.between(latest.getCreatedAt(), now).getSeconds();
            throw new AppException("Wait " + Math.max(1, waitSeconds) + " seconds before requesting another code");
        }

        String code = generateCode();
        OtpCode otp = new OtpCode(mobile, hash(mobile, purpose, code), purpose,
                now.plusMinutes(properties.getTtlMinutes()));
        codes.save(otp);

        deliver(mobile, code);
        log.info("OTP issued for +91{} ({})", mobile, purpose);
    }

    /** @return true when the code matches the latest live OTP for this number */
    @Transactional
    public boolean verify(String rawMobile, String purpose, String code) {
        String mobile = normalize(rawMobile);
        if (code == null || code.trim().isEmpty()) {
            throw new AppException("Enter the OTP we sent you");
        }

        OtpCode otp = codes.findTopByMobileAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(mobile, purpose)
                .orElseThrow(() -> new AppException("No active code for this number — request a new one"));

        if (otp.isExpired()) {
            throw new AppException("That code has expired — request a new one");
        }
        if (otp.getAttempts() >= properties.getMaxAttempts()) {
            throw new AppException("Too many wrong attempts — request a new code");
        }

        otp.setAttempts(otp.getAttempts() + 1);
        String submitted = code.trim();
        if (!otp.getCodeHash().equals(hash(mobile, purpose, submitted))) {
            codes.save(otp);
            int left = properties.getMaxAttempts() - otp.getAttempts();
            throw new AppException(left <= 0
                    ? "Too many wrong attempts — request a new code"
                    : "Incorrect OTP. " + left + " attempt" + (left == 1 ? "" : "s") + " left.");
        }

        otp.setConsumedAt(LocalDateTime.now());
        codes.save(otp);
        return true;
    }

    private void deliver(String mobile, String code) {
        OtpSender primary = msg91 instanceof Msg91OtpSender sender && sender.isConfigured() ? msg91 : null;
        if (primary != null && primary.send(mobile, code, properties.getTtlMinutes())) {
            return;
        }
        if (primary != null) {
            log.warn("MSG91 send failed — falling back to log delivery for +91{}", mobile);
        }
        logging.send(mobile, code, properties.getTtlMinutes());
    }

    private void purgeExpired() {
        try {
            codes.deleteByExpiresAtBefore(LocalDateTime.now().minusDays(1));
        } catch (Exception ex) {
            log.debug("Could not purge expired OTPs: {}", ex.getMessage());
        }
    }

    private String generateCode() {
        int value = ThreadLocalRandom.current().nextInt(0, 1_000_000);
        return String.format(Locale.ROOT, "%0" + CODE_LENGTH + "d", value);
    }

    private String hash(String mobile, String purpose, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(
                    (purpose + "|" + mobile + "|" + code.trim()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
