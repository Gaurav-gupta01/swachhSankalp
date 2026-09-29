package CleanVision.domain;

import lombok.Getter;

/**
 * What a cleanup team handles. Waste reports and animal reports are worked by
 * different crews, so every team is typed and can only receive assignments that
 * match its type.
 */
@Getter
public enum TeamType {

    WASTE("Waste cleanup", "♻️"),
    ANIMAL("Animal rescue", "🐾");

    private final String label;
    private final String icon;

    TeamType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public static TeamType fromApi(String value) {
        if (value == null || value.isBlank()) {
            return WASTE;
        }
        String normalised = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalised);
        } catch (IllegalArgumentException ex) {
            for (TeamType type : values()) {
                if (type.label.equalsIgnoreCase(value.trim())) {
                    return type;
                }
            }
            return WASTE;
        }
    }

    /** A report can only be handed to a team of the matching type. */
    public boolean handles(WasteCategory category) {
        if (category == null) {
            return this == WASTE;
        }
        return category == WasteCategory.ANIMAL ? this == ANIMAL : this == WASTE;
    }
}
