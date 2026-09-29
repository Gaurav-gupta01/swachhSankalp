package CleanVision.domain;

import lombok.Getter;

@Getter
public enum WasteCategory {

    PLASTIC("Plastic", "🥤"),
    PAPER("Paper/Cardboard", "📦"),
    GLASS("Glass", "🍾"),
    ORGANIC("Organic", "🍌"),
    METAL("Metal", "🥫"),
    E_WASTE("E-Waste", "🔋"),
    CONSTRUCTION("Construction", "🏗️"),
    MIXED("Mixed Waste", "🗑️"),
    ANIMAL("Animal", "🐾"),
    OTHER("Other/Unknown", "❓");

    private final String label;
    private final String icon;

    WasteCategory(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    public static WasteCategory fromApi(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        String normalised = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        try {
            return valueOf(normalised);
        } catch (IllegalArgumentException ex) {
            for (WasteCategory category : values()) {
                if (category.label.equalsIgnoreCase(value.trim())) {
                    return category;
                }
            }
            return OTHER;
        }
    }
}
