package CleanVision.ai;

import java.util.List;

import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;

/**
 * Result of the image analysis: what kind of waste it looks like, how sure the
 * model is, and how serious the pile-up is.
 */
public record AiResult(
        WasteCategory category,
        double confidence,
        Severity severity,
        String reason,
        List<Candidate> candidates) {

    public record Candidate(WasteCategory category, double score, double probability) {
    }

    public static AiResult fallback(String reason) {
        return new AiResult(WasteCategory.OTHER, 0.40, Severity.MEDIUM, reason,
                List.of(new Candidate(WasteCategory.OTHER, 1.0, 1.0)));
    }

    public boolean isLowConfidence() {
        return confidence < 0.60;
    }
}
