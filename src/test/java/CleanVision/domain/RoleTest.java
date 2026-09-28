package CleanVision.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void everyRoleLandsOnItsOwnDashboard() {
        assertThat(Role.ADMIN.dashboardPath()).isEqualTo("/admin");
        assertThat(Role.SUPER_ADMIN.dashboardPath()).isEqualTo("/admin");
        assertThat(Role.TEAM_LEADER.dashboardPath()).isEqualTo("/team");
        assertThat(Role.CITIZEN.dashboardPath()).isEqualTo("/my-reports");
    }
}
