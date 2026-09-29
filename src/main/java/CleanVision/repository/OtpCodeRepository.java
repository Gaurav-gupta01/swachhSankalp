package CleanVision.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import CleanVision.domain.OtpCode;

public interface OtpCodeRepository extends JpaRepository<OtpCode, Long> {

    Optional<OtpCode> findTopByMobileAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(
            String mobile, String purpose);

    List<OtpCode> findByMobileAndPurposeAndCreatedAtAfter(String mobile, String purpose,
            LocalDateTime after);

    long countByMobileAndPurposeAndCreatedAtAfter(String mobile, String purpose, LocalDateTime after);

    void deleteByExpiresAtBefore(LocalDateTime cutoff);
}
