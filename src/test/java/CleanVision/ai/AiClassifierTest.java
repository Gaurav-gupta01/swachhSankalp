package CleanVision.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import org.junit.jupiter.api.Test;

import CleanVision.domain.Severity;
import CleanVision.domain.WasteCategory;

class AiClassifierTest {

    private final AiClassifier classifier = new AiClassifier();

    @Test
    void classifiesEveryImageAndReturnsSaneNumbers() {
        for (WasteCategory category : WasteCategory.values()) {
            BufferedImage image = syntheticImage(category, 7L);

            AiResult result = classifier.classify(image);

            assertThat(result.category()).isNotNull();
            assertThat(result.confidence()).isBetween(0.40, 0.97);
            assertThat(result.severity()).isIn(Severity.LOW, Severity.MEDIUM, Severity.HIGH);
            assertThat(result.reason()).isNotBlank();
            assertThat(result.candidates()).isNotEmpty();
        }
    }

    @Test
    void organicTonedImageScoresHighestForOrganic() {
        AiResult result = classifier.classify(syntheticImage(WasteCategory.ORGANIC, 11L));

        assertThat(result.category()).isEqualTo(WasteCategory.ORGANIC);
        assertThat(result.confidence()).isGreaterThan(0.55);
    }

    @Test
    void plasticTonedImageScoresHighestForPlastic() {
        AiResult result = classifier.classify(syntheticImage(WasteCategory.PLASTIC, 13L));

        assertThat(result.category()).isEqualTo(WasteCategory.PLASTIC);
    }

    @Test
    void severityIsNeverNullAndStaysWithinEnumBounds() {
        for (long seed = 0; seed < 12; seed++) {
            AiResult result = classifier.classify(syntheticImage(WasteCategory.MIXED, seed));
            assertThat(result.severity()).isNotNull();
        }
    }

    @Test
    void unreadableImageFallsBackInsteadOfThrowing() {
        AiResult result = classifier.classify(new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }));

        assertThat(result.category()).isEqualTo(WasteCategory.OTHER);
        assertThat(result.confidence()).isEqualTo(0.40);
        assertThat(result.severity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    void lowConfidenceFlagMatchesThreshold() {
        AiResult uncertain = new AiResult(WasteCategory.OTHER, 0.48, Severity.LOW, "uncertain", java.util.List.of());
        AiResult certain = new AiResult(WasteCategory.PLASTIC, 0.93, Severity.HIGH, "clear", java.util.List.of());

        assertThat(uncertain.isLowConfidence()).isTrue();
        assertThat(certain.isLowConfidence()).isFalse();
    }

    private BufferedImage syntheticImage(WasteCategory category, long seed) {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(480, 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        Color[] palette = switch (category) {
            case PLASTIC -> new Color[] { new Color(20, 80, 210), new Color(80, 170, 245), Color.WHITE };
            case ORGANIC -> new Color[] { new Color(60, 140, 40), new Color(140, 110, 45), new Color(95, 75, 35) };
            case PAPER -> new Color[] { new Color(215, 185, 135), new Color(240, 230, 210), new Color(185, 155, 115) };
            case GLASS -> new Color[] { new Color(225, 240, 245), Color.WHITE, new Color(180, 210, 220) };
            case METAL -> new Color[] { new Color(125, 130, 135), new Color(185, 190, 195), new Color(70, 72, 76) };
            case E_WASTE -> new Color[] { new Color(18, 18, 22), new Color(45, 50, 55), new Color(30, 120, 60) };
            case CONSTRUCTION -> new Color[] { new Color(150, 145, 135), new Color(180, 95, 70), new Color(205, 195, 180) };
            default -> new Color[] { new Color(60, 120, 200), new Color(90, 150, 60), new Color(200, 60, 60) };
        };

        g.setColor(switch (category) {
            case E_WASTE -> new Color(30, 30, 34);
            case ORGANIC -> new Color(110, 120, 80);
            case PAPER -> new Color(228, 220, 205);
            case CONSTRUCTION -> new Color(170, 165, 155);
            default -> new Color(140, 145, 150);
        });
        g.fillRect(0, 0, 480, 360);

        for (int i = 0; i < 40; i++) {
            g.setColor(palette[random.nextInt(palette.length)]);
            int w = 30 + random.nextInt(120);
            int h = 25 + random.nextInt(90);
            if (random.nextBoolean()) {
                g.fillOval(random.nextInt(480) - 30, random.nextInt(360) - 20, w, h);
            } else {
                g.fillRect(random.nextInt(480) - 30, random.nextInt(360) - 20, w, h);
            }
        }
        g.dispose();
        return image;
    }
}
