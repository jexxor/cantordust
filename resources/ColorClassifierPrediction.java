package resources;

import java.awt.*;

public class ColorClassifierPrediction extends ColorSource {
    Hilbert map;
    double step;

    private ClassifierModel classifier;
    private final Rgb[] classColors;
    private int baseOffset = 0;
    private boolean classifierInitAttempted = false;

    public ColorClassifierPrediction(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "classifierPrediction";
        this.classifier = cantordust.getClassifier();
        this.classColors = buildClassColors();
    }

    @Override
    public Rgb getPoint(int x) {
        this.classifier = getOrInitClassifier();
        int absoluteIndex = Math.max(0, baseOffset + x);
        int classification = 0;
        if(this.classifier != null) {
            try {
                classification = this.classifier.classAtIndex(absoluteIndex);
            } catch(RuntimeException ignored) {
                classification = 0;
            }
        }
        int clampedClass = Math.max(0, Math.min(classColors.length - 1, classification));
        return classColors[clampedClass];
    }

    @Override
    public int pointArgb(int x) {
        this.classifier = getOrInitClassifier();
        int absoluteIndex = Math.max(0, baseOffset + x);
        int classification = 0;
        if(this.classifier != null) {
            try {
                classification = this.classifier.classAtIndex(absoluteIndex);
            } catch(RuntimeException ignored) {
                classification = 0;
            }
        }
        int clampedClass = Math.max(0, Math.min(classColors.length - 1, classification));
        Rgb color = classColors[clampedClass];
        return (0xFF << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }

    public void setBaseOffset(int baseOffset) {
        this.baseOffset = Math.max(0, baseOffset);
    }

    private ClassifierModel getOrInitClassifier() {
        if(this.classifier != null) {
            return this.classifier;
        }

        this.classifier = cantordust.getClassifier();
        if(this.classifier == null && !classifierInitAttempted) {
            classifierInitAttempted = true;
            cantordust.initiateClassifier();
            this.classifier = cantordust.getClassifier();
        }
        return this.classifier;
    }

    private Rgb[] buildClassColors() {
        int classCount = Math.max(1, ClassifierModel.classes.length);
        Rgb[] colors = new Rgb[classCount];
        for(int classIndex = 0; classIndex < classCount; classIndex++) {
            double c = (double)classIndex / (double)classCount;
            double waveLength = 400 + c * (800 - 400);
            Color color = WavelengthToRGB.waveLengthToRGB(waveLength);
            colors[classIndex] = new Rgb(color.getRed(), color.getGreen(), color.getBlue());
        }
        return colors;
    }
}
