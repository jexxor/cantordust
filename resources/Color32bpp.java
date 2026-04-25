package resources;

public class Color32bpp extends ColorSource { /* see binvis - ColorHilbert class */
    Hilbert map;
    double step;
    public Color32bpp(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "32bpp";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 4)));
        int b0 = data[clampedX] & 0xFF;
        int b1 = (clampedX + 1 < data.length) ? (data[clampedX + 1] & 0xFF) : b0;
        int b2 = (clampedX + 2 < data.length) ? (data[clampedX + 2] & 0xFF) : b1;
        return new Rgb(b2, b1, b0);
    }
}