package resources;

import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class RenderSettings {
    private static volatile boolean antiAliasingEnabled = true;
    private static volatile boolean interpolationEnabled = false;

    private RenderSettings() {
    }

    public static boolean isAntialiasingEnabled() {
        return antiAliasingEnabled;
    }

    public static void setAntialiasingEnabled(boolean enabled) {
        antiAliasingEnabled = enabled;
    }

    public static boolean isInterpolationEnabled() {
        return interpolationEnabled;
    }

    public static void setInterpolationEnabled(boolean enabled) {
        interpolationEnabled = enabled;
    }

    public static void applyImageRenderingHints(Graphics2D graphics) {
        if(graphics == null) {
            return;
        }
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                interpolationEnabled ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                antiAliasingEnabled ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                (interpolationEnabled || antiAliasingEnabled) ? RenderingHints.VALUE_RENDER_QUALITY : RenderingHints.VALUE_RENDER_SPEED);
        graphics.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION,
                interpolationEnabled ? RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY : RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
    }
}
