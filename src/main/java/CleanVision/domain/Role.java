package CleanVision.domain;

import lombok.Getter;

@Getter
public enum Role {

    SUPER_ADMIN("Super Admin"),
    ADMIN("Municipal Officer"),
    TEAM_LEADER("Team Leader"),
    CITIZEN("Citizen");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public boolean isOfficer() {
        return this == ADMIN || this == SUPER_ADMIN;
    }

    public boolean isTeamLeader() {
        return this == TEAM_LEADER;
    }

    public boolean canReport() {
        return this == CITIZEN;
    }

    public static Role fromApi(String value) {
        if (value == null || value.isBlank()) {
            return CITIZEN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return CITIZEN;
        }
    }
}
