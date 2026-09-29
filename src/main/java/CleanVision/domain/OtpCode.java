package CleanVision.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single mobile OTP issued to a citizen. Only the SHA-256 hash of the code is stored —
 * the plaintext exists for seconds inside the SMS payload and in the delivery log fallback.
 */
@Entity
@Table(name = "otp_codes")
@Getter
@Setter
@NoArgsConstructor
public class OtpCode {

    public static final String PURPOSE_REGISTER = "REGISTER";
    public static final String PURPOSE_LOGIN = "LOGIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised 10-digit Indian number without country code. */
    @Column(nullable = false, length = 20)
    private String mobile;

    @Column(name = "code_hash", nullable = false, length = 80)
    private String codeHash;

    @Column(nullable = false, length = 20)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public OtpCode(String mobile, String codeHash, String purpose, LocalDateTime expiresAt) {
        this.mobile = mobile;
        this.codeHash = codeHash;
        this.purpose = purpose;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isUsable() {
        return !isConsumed() && !isExpired();
    }
}
