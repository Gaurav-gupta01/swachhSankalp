package CleanVision.domain;

import lombok.Getter;

@Getter
public enum Role {

    CITIZEN("Citizen"),
    ADMIN("Municipal Officer");

    private final String label;

    Role(String label) {
        this.label = label;
    }
}
