package resources;

public class ColorEntropy extends ColorSource {
    protected Utils utils;
    public ColorEntropy(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "entropy";
        this.utils = new Utils(this.cantordust);
    }

    public double curve(double v) {
        double f = Math.pow((4 * v - 4*Math.pow(v, 2)), 4);
        f = Math.max(f, 0);
        return f;
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int symbolCount = Math.max(1, this.symbol_map.size());
        if(data.length < 2 || symbolCount < 2) {
            return new Rgb(0, 0, 0);
        }
        int blockSize = Math.min(32, data.length);
        double e = this.utils.entropy(this.data, blockSize, clampedX, symbolCount);
        double r;
        if (e > 0.5) {
            r = curve(e - 0.5);
        } else {
            r = 0;
        }
        double b = Math.pow(e, 2);
        return new Rgb(
            (int)(255 * Math.max(0, Math.min(1, r))), 
            0, 
            (int)(255 * Math.max(0, Math.min(1, b)))
        );
    }
}