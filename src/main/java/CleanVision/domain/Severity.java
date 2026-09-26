package CleanVision.domain;

import lombok.Getter;

@Getter
public enum Severity {

    LOW("Low", "Small amount of waste"),
    MEDIUM("Medium", "Moderate waste accumulation"),
    HIGH("High", "Large accumulation or hazardous waste");

    private final String label;
    private final String reason;

    Severity(String label, String reason) {
        this.label = label;
        this.reason = reason;
    }

    public int barPercent() {
        return switch (this) {
            case LOW -> 33;
            case MEDIUM -> 66;
            case HIGH -> 100;
        };
    }

    public static Severity fromApi(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return MEDIUM;
        }
    }
}
