package resources;

public class ColorSpectrum extends ColorSource { /* see binvis - ColorHilbert class */
    private static final int BYTE_SPACE = 256;
    private final Hilbert map;
    private final double step;
    private final Rgb[] spectrumLut = new Rgb[BYTE_SPACE];

    public ColorSpectrum(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "spectrum";
        this.map = new Hilbert(this.cantordust, 3, (Math.pow(256, 3)));
        this.step = (map.getLength() - 1) / (double)(BYTE_SPACE - 1);
        initializeSpectrumLut();
    }

    private void initializeSpectrumLut() {
        int maxIndex = Math.max(0, map.getLength() - 1);
        for(int value = 0; value < BYTE_SPACE; value++) {
            int mappedIndex = Math.max(0, Math.min(maxIndex, (int)Math.round(value * this.step)));
            spectrumLut[value] = (Rgb)map.point(mappedIndex);
        }
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int unsignedByte = data[clampedX] & 0xFF;
        Rgb color = spectrumLut[unsignedByte];
        return color != null ? color : new Rgb(0, 0, 0);
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int unsignedByte = data[clampedX] & 0xFF;
        Rgb color = spectrumLut[unsignedByte];
        if(color == null) {
            return 0xFF000000;
        }
        return (0xFF << 24) | (color.r << 16) | (color.g << 8) | color.b;
    }
}