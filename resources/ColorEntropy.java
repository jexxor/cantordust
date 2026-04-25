package resources;

public class ColorEntropy extends ColorSource {
    private static final int ENTROPY_BLOCK_SIZE = 32;
    private final int[] histogram = new int[256];
    private final int[] touchedBins = new int[256];

    public ColorEntropy(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "entropy";
    }

    public double curve(double v) {
        double f = Math.pow((4 * v - 4*Math.pow(v, 2)), 4);
        f = Math.max(f, 0);
        return f;
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        if(data.length < 2) {
            return 0xFF000000;
        }
        int blockSize = Math.min(ENTROPY_BLOCK_SIZE, data.length);
        int start = clampedX - (blockSize / 2);
        start = Math.max(0, Math.min(start, data.length - blockSize));
        int end = Math.min(data.length, start + blockSize);
        int sampleCount = end - start;
        if(sampleCount <= 1) {
            return 0xFF000000;
        }

        int touchedCount = 0;
        for(int i = start; i < end; i++) {
            int symbol = data[i] & 0xFF;
            if(histogram[symbol] == 0) {
                touchedBins[touchedCount++] = symbol;
            }
            histogram[symbol]++;
        }

        int symbolCount = Math.min(256, data.length);
        int base = Math.max(2, Math.min(sampleCount, symbolCount));
        double inverseLogBase = 1.0 / Math.log(base);
        double entropy = 0.0;
        for(int i = 0; i < touchedCount; i++) {
            int symbol = touchedBins[i];
            int count = histogram[symbol];
            histogram[symbol] = 0;
            if(count == 0) {
                continue;
            }
            double p = count / (double)sampleCount;
            entropy += p * (Math.log(p) * inverseLogBase);
        }
        double e = -entropy;
        double r;
        if (e > 0.5) {
            r = curve(e - 0.5);
        } else {
            r = 0;
        }
        double b = Math.pow(e, 2);
        int red = (int)(255 * Math.max(0, Math.min(1, r)));
        int blue = (int)(255 * Math.max(0, Math.min(1, b)));
        return (0xFF << 24) | (red << 16) | blue;
    }

    @Override
    public Rgb getPoint(int x) {
        int argb = pointArgb(x);
        return new Rgb((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF);
    }
}