package CleanVision.ai;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;

/**
 * Rule-based waste classifier.
 *
 * <p>It converts the colour / texture measurements in {@link ImageFeatures} into a
 * score for every waste category. The highest scoring category wins and the gap
 * to the runner-up becomes the confidence value. When the model is retrained on
 * a real CNN later only this class has to be swapped - the service and API
 * contract stay identical.</p>
 */
@Component
public class AiClassifier {

    public AiResult classify(InputStream input) {
        try {
            return score(ImageFeatures.extract(input));
        } catch (IOException | RuntimeException ex) {
            return AiResult.fallback("Image could not be analysed: " + ex.getMessage());
        }
    }

    public AiResult classify(BufferedImage image) {
        try {
            return score(ImageFeatures.extract(image));
        } catch (IOException | RuntimeException ex) {
            return AiResult.fallback("Image could not be analysed: " + ex.getMessage());
        }
    }

    public AiResult score(ImageFeatures f) {
        Map<WasteCategory, Double> scores = new EnumMap<>(WasteCategory.class);

        double notEdge = 1 - f.getEdgeDensity();
        double midBrightness = 1 - Math.abs(f.getBrightness() - 0.55);

        scores.put(WasteCategory.PLASTIC,
                0.35 + 1.7 * f.getBlueRatio() + 1.0 * f.getHighSatRatio() + 0.6 * f.getBrightRatio()
                        + 0.5 * f.getEdgeDensity() + 0.4 * f.getColorfulness());

        scores.put(WasteCategory.PAPER,
                0.30 + 1.4 * f.getWarmBrownRatio() + 1.1 * f.getLowSatBrightRatio()
                        + 0.5 * notEdge * midBrightness + 0.3 * (1 - f.getEntropy()));

        scores.put(WasteCategory.GLASS,
                0.20 + 1.3 * f.getLowSatBrightRatio() * (0.4 + f.getBrightness()) + 1.1 * f.getEdgeDensity()
                        + 0.7 * f.getContrast() + 0.4 * f.getHighSatRatio() * 0.5);

        scores.put(WasteCategory.ORGANIC,
                0.30 + 1.9 * f.getGreenRatio() + 1.2 * f.getOrganicBrownRatio() + 0.6 * notEdge * (1 - f.getBrightness() * 0.4)
                        + 0.4 * f.getEntropy());

        scores.put(WasteCategory.METAL,
                0.20 + 1.0 * f.getLowSatBrightRatio() + 1.3 * f.getEdgeDensity() + 0.9 * f.getContrast()
                        + 0.5 * f.getBrightRatio());

        scores.put(WasteCategory.E_WASTE,
                0.20 + 1.3 * f.getDarkRatio() + 1.2 * f.getEdgeDensity() + 0.9 * f.getContrast()
                        + 1.0 * f.getSaturatedPatchRatio() + 0.5 * f.getHueSpread());

        scores.put(WasteCategory.CONSTRUCTION,
                0.25 + 1.2 * f.getGrayDustyRatio() + 1.1 * f.getEdgeDensity() + 0.8 * f.getEntropy()
                        + 0.5 * f.getDarkRatio());

        scores.put(WasteCategory.MIXED,
                0.15 + 1.3 * f.getEntropy() + 1.1 * f.getHueVariety() + 1.0 * (1 - f.getDominance())
                        + 0.6 * f.getColorfulness());

        scores.put(WasteCategory.OTHER, 0.30 + 0.3 * notEdge + 0.2 * (1 - f.getColorfulness()));

        List<Map.Entry<WasteCategory, Double>> ranked = new ArrayList<>(scores.entrySet());
        ranked.sort(Map.Entry.<WasteCategory, Double>comparingByValue().reversed());

        WasteCategory top = ranked.get(0).getKey();
        double topScore = ranked.get(0).getValue();
        double secondScore = ranked.get(1).getValue();

        double confidence = 0.45 + 0.50 * (topScore / (topScore + secondScore + 1e-6));
        if (f.getContrast() < 0.10) {
            confidence *= 0.85;
        }
        confidence = clamp(confidence, 0.40, 0.97);

        Severity severity = severity(top, f);

        List<AiResult.Candidate> candidates = new ArrayList<>();
        for (int idx = 0; idx < Math.min(3, ranked.size()); idx++) {
            Map.Entry<WasteCategory, Double> entry = ranked.get(idx);
            candidates.add(new AiResult.Candidate(entry.getKey(), round(entry.getValue()),
                    round(entry.getValue() / (topScore + secondScore + ranked.get(2).getValue() + 1e-6))));
        }

        return new AiResult(top, round(confidence), severity, reason(top, severity, f), candidates);
    }

    private Severity severity(WasteCategory category, ImageFeatures f) {
        double pileIndex = 0.40 * f.getEdgeDensity() + 0.30 * f.getDarkRatio() + 0.30 * f.getEntropy();

        Severity severity;
        if (pileIndex < 0.36) {
            severity = Severity.LOW;
        } else if (pileIndex < 0.58) {
            severity = Severity.MEDIUM;
        } else {
            severity = Severity.HIGH;
        }

        if (severity == Severity.LOW && (category == WasteCategory.E_WASTE || category == WasteCategory.CONSTRUCTION
                || category == WasteCategory.GLASS)) {
            severity = Severity.MEDIUM;
        }
        if (f.getDarkRatio() > 0.5 && f.getEdgeDensity() > 0.5) {
            severity = Severity.HIGH;
        }
        return severity;
    }

    private String reason(WasteCategory category, Severity severity, ImageFeatures f) {
        StringBuilder reason = new StringBuilder();
        switch (category) {
            case PLASTIC -> reason.append("Blue and highly saturated packaging-like colours detected");
            case PAPER -> reason.append("Warm matte brown and low-saturation surfaces detected");
            case GLASS -> reason.append("Bright reflective surfaces with sharp edges detected");
            case ORGANIC -> reason.append("Green and brown organic tones with soft texture detected");
            case METAL -> reason.append("Grey reflective tones with strong edges detected");
            case E_WASTE -> reason.append("Dark background with sharp edges and scattered colour patches");
            case CONSTRUCTION -> reason.append("Grey dusty surfaces with rough high-frequency texture");
            case MIXED -> reason.append("Many different colours and textures with no dominant material");
            default -> reason.append("No dominant material pattern found in the image");
        }
        if (severity == Severity.HIGH) {
            reason.append("; large dark textured accumulation detected");
        } else if (severity == Severity.MEDIUM) {
            reason.append("; moderate accumulation detected");
        } else {
            reason.append("; small scattered waste detected");
        }
        return reason.toString();
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
