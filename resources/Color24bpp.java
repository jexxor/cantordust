package resources;

import java.awt.Color;
public class Color24bpp extends ColorSource { /* see binvis - ColorHilbert class */
    Hilbert map;
    double step;
    public Color24bpp(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "24bpp";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 3)));
        int b0 = data[clampedX] & 0xFF;
        int b1 = (clampedX + 1 < data.length) ? (data[clampedX + 1] & 0xFF) : b0;
        int b2 = (clampedX + 2 < data.length) ? (data[clampedX + 2] & 0xFF) : b1;
        int pixel = (b2 << 16) | (b1 << 8) | b0;
        Color r = new Color(pixel);
        Rgb rgb = new Rgb(r.getRed(), r.getGreen(), r.getBlue());
        return rgb;
    }
}