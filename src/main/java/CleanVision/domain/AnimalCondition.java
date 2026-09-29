package CleanVision.domain;

import lombok.Getter;

/**
 * Condition of a reported animal. The severity of the report is derived from it so
 * animal reports slot straight into the existing priority queues.
 */
@Getter
public enum AnimalCondition {

    ALIVE("Alive / roaming", "🟢", Severity.LOW),
    INJURED("Injured or sick", "🩹", Severity.MEDIUM),
    DEAD("Dead carcass", "☠️", Severity.HIGH);

    private final String label;
    private final String icon;
    private final Severity severity;

    AnimalCondition(String label, String icon, Severity severity) {
        this.label = label;
        this.icon = icon;
        this.severity = severity;
    }

    /** Strict lookup — returns empty when the value is not a known condition. */
    public static java.util.Optional<AnimalCondition> find(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalised = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (AnimalCondition condition : values()) {
            if (condition.name().equals(normalised) || condition.label.equalsIgnoreCase(value.trim())) {
                return java.util.Optional.of(condition);
            }
        }
        return java.util.Optional.empty();
    }

    public static AnimalCondition fromApi(String value) {
        return find(value).orElse(ALIVE);
    }
}
