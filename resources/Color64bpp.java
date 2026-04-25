package resources;

public class Color64bpp extends ColorSource { /* see binvis - ColorHilbert class */
    Hilbert map;
    double step;
    public Color64bpp(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "64bpp";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }

        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 8)));
        int blue16 = readUint16LE(clampedX);
        int green16 = readUint16LE(clampedX + 2);
        int red16 = readUint16LE(clampedX + 4);
        int alpha16 = readUint16LE(clampedX + 6);

        int red = (red16 * 255 + 32767) / 65535;
        int green = (green16 * 255 + 32767) / 65535;
        int blue = (blue16 * 255 + 32767) / 65535;

        if(alpha16 == 0) {
            return new Rgb(0, 0, 0);
        }
        return new Rgb(red, green, blue);
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }

        int clampedX = Math.max(0, Math.min(x, Math.max(0, data.length - 8)));
        int blue16 = readUint16LE(clampedX);
        int green16 = readUint16LE(clampedX + 2);
        int red16 = readUint16LE(clampedX + 4);
        int alpha16 = readUint16LE(clampedX + 6);

        int red = (red16 * 255 + 32767) / 65535;
        int green = (green16 * 255 + 32767) / 65535;
        int blue = (blue16 * 255 + 32767) / 65535;
        if(alpha16 == 0) {
            return 0xFF000000;
        }
        return (0xFF << 24) | (red << 16) | (green << 8) | blue;
    }

    private int readUint16LE(int offset) {
        int lo = (offset >= 0 && offset < data.length) ? (data[offset] & 0xFF) : 0;
        int hi = (offset + 1 >= 0 && offset + 1 < data.length) ? (data[offset + 1] & 0xFF) : lo;
        return lo | (hi << 8);
    }
}