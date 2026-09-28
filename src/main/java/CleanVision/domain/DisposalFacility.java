package CleanVision.domain;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An authorised disposal / processing facility that a cleanup team can route
 * collected waste to. Accepted waste types are stored as a comma separated
 * list of {@link WasteCategory} names.
 */
@Entity
@Table(name = "disposal_facilities")
@Getter
@Setter
@NoArgsConstructor
public class DisposalFacility {

    public enum FacilityType {
        MATERIAL_RECOVERY("Material Recovery Facility"),
        RECYCLING("Recycling Centre"),
        COMPOSTING("Composting / Biogas Facility"),
        E_WASTE("Authorised E-Waste Facility"),
        CND("C&D Waste Processing Facility"),
        LANDFILL("Engineered Landfill"),
        TRANSFER_STATION("Collection & Transfer Station");

        private final String label;

        FacilityType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false, length = 40)
    private FacilityType facilityType = FacilityType.MATERIAL_RECOVERY;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(length = 255)
    private String address;

    @Column(length = 60)
    private String ward;

    /** Comma separated {@link WasteCategory} names this facility accepts. */
    @Column(name = "accepted_waste_types", nullable = false, length = 255)
    private String acceptedWasteTypes = WasteCategory.MIXED.name();

    @Column(length = 60)
    private String capacity;

    @Column(name = "operational", nullable = false)
    private boolean operational = true;

    @Column(length = 30)
    private String contact;

    public Set<WasteCategory> getAcceptedCategories() {
        if (acceptedWasteTypes == null || acceptedWasteTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(acceptedWasteTypes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    try {
                        return WasteCategory.valueOf(value.toUpperCase().replace('-', '_'));
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                })
                .filter(category -> category != null && category != WasteCategory.OTHER)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public boolean accepts(WasteCategory category) {
        return getAcceptedCategories().contains(category);
    }
}
