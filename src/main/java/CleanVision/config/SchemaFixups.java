package CleanVision.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Corrective schema fixes for databases that were created before a column relaxed a constraint
 * or before a column existed at all.
 *
 * <p>Spring's {@code ddl-auto=update} only adds missing tables and columns — it never relaxes an
 * existing NOT NULL constraint (so {@code reports.image_url} stays required on older databases
 * even though animal reports may be filed without a photo) and it never back-fills values for a
 * newly added column (so existing teams come back as an empty team type and existing staff
 * accounts come back with an unverified mobile).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaFixups implements ApplicationRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(ApplicationArguments args) {
        allowPhotoLessReports();
        backfillTeamType();
        markStaffMobilesVerified();
    }

    private void allowPhotoLessReports() {
        try {
            Integer required = count(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reports' "
                            + "AND COLUMN_NAME = 'image_url' AND IS_NULLABLE = 'NO'");
            if (required == 0) {
                return;
            }
            jdbc.execute("ALTER TABLE reports MODIFY COLUMN image_url VARCHAR(255) NULL");
            log.info("reports.image_url relaxed to NULL so animal reports can be filed without a photo");
        } catch (Exception ex) {
            log.warn("Could not relax reports.image_url nullability: {}", ex.getMessage());
        }
    }

    /** Teams created before the waste/animal split default to waste cleanup. */
    private void backfillTeamType() {
        try {
            if (count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'teams' "
                    + "AND COLUMN_NAME = 'team_type'") == 0) {
                return;
            }
            int updated = jdbc.update(
                    "UPDATE teams SET team_type = 'WASTE' WHERE team_type IS NULL OR team_type = ''");
            if (updated > 0) {
                log.info("Backfilled {} existing team(s) as waste cleanup teams", updated);
            }
        } catch (Exception ex) {
            log.warn("Could not backfill teams.team_type: {}", ex.getMessage());
        }
    }

    /**
     * Only self-registered citizens must prove their mobile with an OTP. Staff accounts are
     * created by an officer, so they are treated as already verified and can never get stuck
     * behind the login gate.
     */
    private void markStaffMobilesVerified() {
        try {
            if (count("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' "
                    + "AND COLUMN_NAME = 'phone_verified'") == 0) {
                return;
            }
            int updated = jdbc.update(
                    "UPDATE users SET phone_verified = TRUE "
                            + "WHERE role <> 'CITIZEN' AND (phone_verified IS NULL OR phone_verified = FALSE)");
            if (updated > 0) {
                log.info("Marked {} staff account(s) as mobile-verified", updated);
            }
        } catch (Exception ex) {
            log.warn("Could not mark staff accounts verified: {}", ex.getMessage());
        }
    }

    private int count(String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
