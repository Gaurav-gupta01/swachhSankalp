package CleanVision.domain;

import lombok.Getter;

@Getter
public enum AnimalType {

    DOG("Stray dog", "🐕"),
    CAT("Cat", "🐈"),
    COW("Cow", "🐄"),
    HORSE("Horse", "🐎"),
    PIG("Pig", "🐖"),
    MONKEY("Monkey", "🐒"),
    GOAT("Goat", "🐐"),
    BIRD("Bird", "🐦"),
    OTHER("Other animal", "🐾");

    private final String label;
    private final String icon;

    AnimalType(String label, String icon) {
        this.label = label;
        this.icon = icon;
    }

    /** Strict lookup — returns empty when the value is not a known animal. */
    public static java.util.Optional<AnimalType> find(String value) {
        if (value == null || value.isBlank()) {
            return java.util.Optional.empty();
        }
        String normalised = value.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        for (AnimalType type : values()) {
            if (type.name().equals(normalised) || type.label.equalsIgnoreCase(value.trim())) {
                return java.util.Optional.of(type);
            }
        }
        return java.util.Optional.empty();
    }

    public static AnimalType fromApi(String value) {
        return find(value).orElse(OTHER);
    }
}
