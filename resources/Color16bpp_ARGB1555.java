package resources;

public class Color16bpp_ARGB1555 extends ColorSource { /* see binvis - ColorHilbert class */
    Hilbert map;
    double step;
    public Color16bpp_ARGB1555(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "16bpp ARGB1555";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }

        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 2)));
        int lowByte = data[clampedX] & 0xFF;
        int highByte = (clampedX + 1 < data.length) ? (data[clampedX + 1] & 0xFF) : lowByte;

        int alphaBit = (highByte & 0x80) >>> 7;
        int red5 = (highByte & 0x7C) >>> 2;
        int green5 = ((highByte & 0x03) << 3) | ((lowByte & 0xE0) >>> 5);
        int blue5 = lowByte & 0x1F;

        // Use integer scaling to avoid quantization from integer division.
        int red = (red5 * 255 + 15) / 31;
        int green = (green5 * 255 + 15) / 31;
        int blue = (blue5 * 255 + 15) / 31;

        // MetricMap consumes opaque RGB only, so treat 1-bit alpha as visible/hidden.
        if(alphaBit == 0) {
            return new Rgb(0, 0, 0);
        }
        return new Rgb(red, green, blue);
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }

        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 2)));
        int lowByte = data[clampedX] & 0xFF;
        int highByte = (clampedX + 1 < data.length) ? (data[clampedX + 1] & 0xFF) : lowByte;

        int alphaBit = (highByte & 0x80) >>> 7;
        int red5 = (highByte & 0x7C) >>> 2;
        int green5 = ((highByte & 0x03) << 3) | ((lowByte & 0xE0) >>> 5);
        int blue5 = lowByte & 0x1F;

        int red = (red5 * 255 + 15) / 31;
        int green = (green5 * 255 + 15) / 31;
        int blue = (blue5 * 255 + 15) / 31;
        if(alphaBit == 0) {
            return 0xFF000000;
        }
        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }
}