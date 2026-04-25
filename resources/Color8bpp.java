package resources;

public class Color8bpp extends ColorSource { /* see binvis - ColorHilbert class */
    Hilbert map;
    double step;
    public Color8bpp(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "8bpp";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int unsignedByte = data[clampedX] & 0xFF;
        return new Rgb(0, unsignedByte, 0);
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int unsignedByte = data[clampedX] & 0xFF;
        return (0xFF << 24) | (unsignedByte << 8);
    }
}