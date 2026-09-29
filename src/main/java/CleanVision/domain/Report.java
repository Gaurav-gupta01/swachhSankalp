package CleanVision.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User reporter;

    @Column(name = "image_url", length = 255)
    private String imageUrl;

    @Column(name = "after_image_url", length = 255)
    private String afterImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "waste_type", nullable = false, length = 30)
    private WasteCategory wasteType;

    @Column(nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Severity severity = Severity.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_type", length = 30)
    private AnimalType animalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "animal_condition", length = 30)
    private AnimalCondition animalCondition;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(length = 255)
    private String address;

    @Column(length = 60)
    private String ward;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status = ReportStatus.SUBMITTED;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    @Column(name = "quantity_kg")
    private Double quantityKg;

    @Column(name = "team_latitude")
    private Double teamLatitude;

    @Column(name = "team_longitude")
    private Double teamLongitude;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "disposal_facility_id")
    private DisposalFacility disposalFacility;

    @Column(name = "disposed_at")
    private LocalDateTime disposedAt;

    @Column(name = "route_distance_m")
    private Double routeDistanceM;

    @Column(name = "route_duration_s")
    private Double routeDurationS;
}
