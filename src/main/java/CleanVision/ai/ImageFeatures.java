package CleanVision.ai;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import lombok.Getter;

/**
 * Numeric description of an uploaded picture. Everything the classifier needs is
 * computed once here so the scoring rules stay readable.
 */
@Getter
public class ImageFeatures {

    private static final int WIDTH = 224;
    private static final int HEIGHT = 224;
    private static final int HUE_BINS = 24;

    private final double brightness;
    private final double contrast;
    private final double colorfulness;
    private final double edgeDensity;
    private final double entropy;

    private final double darkRatio;
    private final double brightRatio;
    private final double lowSatBrightRatio;
    private final double grayDustyRatio;

    private final double greenRatio;
    private final double warmBrownRatio;
    private final double organicBrownRatio;
    private final double blueRatio;
    private final double highSatRatio;
    private final double saturatedPatchRatio;

    private final double hueVariety;
    private final double dominance;
    private final double hueSpread;

    private ImageFeatures(double brightness, double contrast, double colorfulness, double edgeDensity,
            double entropy, double darkRatio, double brightRatio, double lowSatBrightRatio,
            double grayDustyRatio, double greenRatio, double warmBrownRatio, double organicBrownRatio,
            double blueRatio, double highSatRatio, double saturatedPatchRatio, double hueVariety,
            double dominance, double hueSpread) {
        this.brightness = brightness;
        this.contrast = contrast;
        this.colorfulness = colorfulness;
        this.edgeDensity = edgeDensity;
        this.entropy = entropy;
        this.darkRatio = darkRatio;
        this.brightRatio = brightRatio;
        this.lowSatBrightRatio = lowSatBrightRatio;
        this.grayDustyRatio = grayDustyRatio;
        this.greenRatio = greenRatio;
        this.warmBrownRatio = warmBrownRatio;
        this.organicBrownRatio = organicBrownRatio;
        this.blueRatio = blueRatio;
        this.highSatRatio = highSatRatio;
        this.saturatedPatchRatio = saturatedPatchRatio;
        this.hueVariety = hueVariety;
        this.dominance = dominance;
        this.hueSpread = hueSpread;
    }

    public static ImageFeatures extract(InputStream input) throws IOException {
        BufferedImage image = ImageIO.read(input);
        if (image == null) {
            throw new IOException("Unsupported or corrupt image file");
        }
        return extract(image);
    }

    public static ImageFeatures extract(BufferedImage source) throws IOException {
        BufferedImage image = resize(source);
        int w = image.getWidth();
        int h = image.getHeight();
        int n = w * h;

        double[] luma = new double[n];
        double[] hueMass = new double[HUE_BINS];
        int[] lumHist = new int[32];

        double sumBrightness = 0;
        double sumSat = 0;
        double lumaSum = 0;
        int dark = 0;
        int bright = 0;
        int lowSatBright = 0;
        int grayDusty = 0;
        int green = 0;
        int warmBrown = 0;
        int organicBrown = 0;
        int blue = 0;
        int highSat = 0;
        int saturatedPatch = 0;

        float[] hsb = new float[3];
        double rgSum = 0;
        double rgSqSum = 0;
        double ybSum = 0;
        double ybSqSum = 0;

        int i = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++, i++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                Color.RGBtoHSB(r, g, b, hsb);
                double hueDeg = hsb[0] * 360.0;
                double sat = hsb[1];
                double val = hsb[2];

                double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                luma[i] = l;
                lumaSum += l;
                lumHist[Math.min(31, (int) (l / 8))]++;

                sumBrightness += val;
                sumSat += sat;
                if (val < 0.28) {
                    dark++;
                }
                if (val > 0.88) {
                    bright++;
                }
                if (sat < 0.18 && val > 0.55) {
                    lowSatBright++;
                }
                if (sat < 0.28 && val > 0.22 && val < 0.80) {
                    grayDusty++;
                }
                if (sat > 0.42) {
                    highSat++;
                }

                if (sat > 0.15 && val > 0.12) {
                    int bin = Math.min(HUE_BINS - 1, (int) (hueDeg / (360.0 / HUE_BINS)));
                    hueMass[bin] += sat;
                }

                boolean isGreen = hueDeg >= 70 && hueDeg < 165 && sat > 0.18;
                boolean isBlue = hueDeg >= 195 && hueDeg < 255 && sat > 0.18;
                if (isGreen) {
                    green++;
                }
                if (isBlue) {
                    blue++;
                }
                if (hueDeg >= 14 && hueDeg < 52 && sat > 0.18 && sat < 0.85 && val > 0.18 && val < 0.82) {
                    warmBrown++;
                    organicBrown++;
                }
                if (hueDeg >= 20 && hueDeg < 45 && sat > 0.08 && sat < 0.45 && val > 0.35 && val < 0.9) {
                    warmBrown++;
                }
                if (sat > 0.40 && !isGreen && !isBlue) {
                    saturatedPatch++;
                }

                double rg = r - g;
                double yb = 0.5 * (r + g) - b;
                rgSum += rg;
                rgSqSum += rg * rg;
                ybSum += yb;
                ybSqSum += yb * yb;
            }
        }

        double meanBrightness = sumBrightness / n;
        double lumaMean = lumaSum / n;

        double variance = 0;
        for (int k = 0; k < n; k++) {
            double d = luma[k] - lumaMean;
            variance += d * d;
        }
        double std = Math.sqrt(variance / n);

        double edgeSum = 0;
        for (int y = 0; y < h - 1; y++) {
            for (int x = 0; x < w - 1; x++) {
                int p = y * w + x;
                double gx = Math.abs(luma[p + 1] - luma[p]);
                double gy = Math.abs(luma[p + w] - luma[p]);
                edgeSum += gx + gy;
            }
        }
        double edge = clamp01(edgeSum / ((double) (w - 1) * (h - 1) * 2 * 50));

        double histEntropy = 0;
        for (int count : lumHist) {
            if (count == 0) {
                continue;
            }
            double p = count / (double) n;
            histEntropy -= p * (Math.log(p) / Math.log(2));
        }
        histEntropy = clamp01(histEntropy / 5.0);

        double rgMean = rgSum / n;
        double ybMean = ybSum / n;
        double rgStd = Math.sqrt(Math.max(0, rgSqSum / n - rgMean * rgMean));
        double ybStd = Math.sqrt(Math.max(0, ybSqSum / n - ybMean * ybMean));
        double colorfulness = clamp01((Math.sqrt(rgStd * rgStd + ybStd * ybStd)
                + 0.3 * Math.sqrt(rgMean * rgMean + ybMean * ybMean)) / 110.0);

        double hueTotal = 0;
        for (double mass : hueMass) {
            hueTotal += mass;
        }
        int varietyBins = 0;
        double topBin = 0;
        double hueMeanAngle = 0;
        double hueWeightedMass = 0;
        for (int bin = 0; bin < HUE_BINS; bin++) {
            double share = hueTotal > 0 ? hueMass[bin] / hueTotal : 0;
            if (share > 0.04) {
                varietyBins++;
            }
            topBin = Math.max(topBin, share);
            double angle = (bin + 0.5) * (360.0 / HUE_BINS);
            hueMeanAngle += angle * hueMass[bin];
            hueWeightedMass += hueMass[bin];
        }
        if (hueWeightedMass > 0) {
            hueMeanAngle /= hueWeightedMass;
        }
        double hueVariance = 0;
        if (hueWeightedMass > 0) {
            for (int bin = 0; bin < HUE_BINS; bin++) {
                double angle = (bin + 0.5) * (360.0 / HUE_BINS);
                double d = angularDistance(angle, hueMeanAngle);
                hueVariance += hueMass[bin] * d * d;
            }
            hueVariance /= hueWeightedMass;
        }
        double hueSpread = clamp01(Math.sqrt(hueVariance) / 120.0);

        return new ImageFeatures(
                clamp01(meanBrightness),
                clamp01(std / 90.0),
                colorfulness,
                edge,
                histEntropy,
                dark / (double) n,
                bright / (double) n,
                lowSatBright / (double) n,
                grayDusty / (double) n,
                green / (double) n,
                warmBrown / (double) n,
                organicBrown / (double) n,
                blue / (double) n,
                highSat / (double) n,
                saturatedPatch / (double) n,
                varietyBins / (double) HUE_BINS,
                topBin,
                hueSpread);
    }

    private static double angularDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static BufferedImage resize(BufferedImage source) {
        BufferedImage target = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Src);
        g.drawImage(source, 0, 0, WIDTH, HEIGHT, null);
        g.dispose();
        return target;
    }

    private static double clamp01(double value) {
        return Math.max(0, Math.min(1, value));
    }
}
