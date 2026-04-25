package resources;

public class ColorSpectrum extends ColorSource { /* see binvis - ColorHilbert class */
    private static final int BYTE_SPACE = 256;
    private final Hilbert map;
    private final double step;

    public ColorSpectrum(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "spectrum";
        this.map = new Hilbert(this.cantordust, 3, (Math.pow(256, 3)));
        this.step = (map.getLength() - 1) / (double)(BYTE_SPACE - 1);
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int unsignedByte = data[clampedX] & 0xFF;
        int mappedIndex = Math.max(0, Math.min(map.getLength() - 1, (int)Math.round(unsignedByte * this.step)));
        return (Rgb)map.point(mappedIndex);
    }
}