package CleanVision.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import CleanVision.domain.ReportStatus;
import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

public final class Forms {

    private Forms() {
    }

    @Data
    public static class RegisterForm {

        @NotBlank(message = "Name is required")
        @Size(max = 120)
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        private String email;

        /** Mandatory — proven with an OTP before the account is created. */
        @NotBlank(message = "Mobile number is required")
        @Size(max = 20)
        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 60, message = "Password must be at least 6 characters")
        private String password;
    }

    @Data
    public static class LoginForm {

        @NotBlank
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class ReportForm {

        @NotBlank(message = "Upload an image first")
        private String imageFile;

        private String wasteType;

        private double confidence;

        private String severity;

        private Double latitude;

        private Double longitude;

        @Size(max = 255)
        private String address;

        @Size(max = 60)
        private String ward;

        @Size(max = 500)
        private String description;
    }

    @Data
    public static class AnimalReportForm {

        @NotBlank(message = "Choose the animal you are reporting")
        private String animalType;

        @NotBlank(message = "Tell us the condition of the animal")
        private String animalCondition;

        /** Optional — citizens may report an animal without a photo. */
        private MultipartFile image;

        private Double latitude;

        private Double longitude;

        @Size(max = 255)
        private String address;

        @Size(max = 60)
        private String ward;

        @Size(max = 500)
        private String description;
    }

    @Data
    public static class TeamForm {

        @NotBlank(message = "Team name is required")
        @Size(max = 80)
        private String teamName;

        @Size(max = 60)
        private String ward;

        @Size(max = 80)
        private String leader;

        @Size(max = 30)
        private String contact;

        private boolean active = true;

        /** WASTE or ANIMAL — defaults to waste cleanup. */
        private String teamType = "WASTE";
    }

    @Data
    public static class CreateUserForm {

        @NotBlank(message = "Name is required")
        @Size(max = 120)
        private String name;

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        private String email;

        @Size(max = 20)
        private String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 60, message = "Password must be at least 6 characters")
        private String password;

        @NotBlank(message = "Role is required")
        private String role;

        private Long teamId;
    }

    public record AiClassifyResponse(
            String imageFile,
            String category,
            String wasteType,
            String categoryIcon,
            double confidence,
            String severity,
            String reason,
            boolean lowConfidence,
            List<Map<String, Object>> candidates) {

        public static AiClassifyResponse from(String imageFile, CleanVision.ai.AiResult result) {
            List<Map<String, Object>> candidates = result.candidates().stream()
                    .map(c -> Map.<String, Object>of("wasteType", c.category().name(),
                            "label", c.category().getLabel(),
                            "score", c.score()))
                    .toList();
            return new AiClassifyResponse(
                    imageFile,
                    result.category().name(),
                    result.category().getLabel(),
                    result.category().getIcon(),
                    result.confidence(),
                    result.severity().getLabel(),
                    result.reason(),
                    result.isLowConfidence(),
                    candidates);
        }
    }

    public record ReportSummary(
            String publicId,
            String wasteType,
            String categoryIcon,
            double confidence,
            String severity,
            String status,
            String ward,
            String address,
            String imageUrl,
            String afterImageUrl,
            LocalDateTime createdAt) {
    }

    public record DashboardStatistics(
            long totalReports,
            long pending,
            long inProgress,
            long resolved,
            long disposalPending,
            double averageConfidence,
            Map<String, Long> byCategory,
            Map<String, Long> bySeverity,
            Map<String, Long> byWard,
            List<Map<String, Object>> recentReports,
            List<Map<String, Object>> hotspots,
            List<Map<String, Object>> reportsOverTime) {
    }

    @Data
    public static class FacilityForm {

        @NotBlank(message = "Facility name is required")
        @Size(max = 120)
        private String name;

        @NotBlank(message = "Facility type is required")
        private String facilityType;

        private double latitude;

        private double longitude;

        @Size(max = 255)
        private String address;

        @Size(max = 60)
        private String ward;

        private List<String> acceptedTypes = List.of();

        @Size(max = 60)
        private String capacity;

        private boolean operational = true;

        @Size(max = 30)
        private String contact;
    }

    public record FacilitySuggestionView(
            Long id,
            String name,
            String facilityType,
            String address,
            String ward,
            List<String> acceptedLabels,
            boolean operational,
            Double distanceMeters,
            Double durationSeconds,
            String distanceLabel,
            String durationLabel,
            int score,
            String navigateUrl,
            Double latitude,
            Double longitude) {
    }

    public record ApiResponse(boolean success, String message, Object data) {

        public static ApiResponse ok(String message, Object data) {
            return new ApiResponse(true, message, data);
        }

        public static ApiResponse error(String message) {
            return new ApiResponse(false, message, null);
        }

        public static ApiResponse error(String message, Object data) {
            return new ApiResponse(false, message, data);
        }
    }

    public static Map<String, Object> toMap(WasteCategory category, Severity severity, ReportStatus status) {
        return Map.of("category", category.getLabel(), "severity", severity.getLabel(),
                "status", status.getLabel());
    }
}
