package resources;

import java.awt.*;

public class ColorClassifierPrediction extends ColorSource {
    Hilbert map;
    double step;

    private ClassifierModel classifier;
    private int baseOffset = 0;
    private boolean classifierInitAttempted = false;

    public ColorClassifierPrediction(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "classifierPrediction";
        this.classifier = cantordust.getClassifier();
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
        double c = (double)classification / (double)ClassifierModel.classes.length;
        double waveLength = 400 + c*(800-400);
        Color color = WavelengthToRGB.waveLengthToRGB(waveLength);
        return new Rgb(color.getRed(), color.getGreen(), color.getBlue());
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
}
