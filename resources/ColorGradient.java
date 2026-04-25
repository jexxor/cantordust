package resources;

public class ColorGradient extends ColorSource {
    public ColorGradient(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "gradient";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        double c = (data[clampedX] & 0xFF) / 255.0;
        return new Rgb(
            (int)(255*c), 
            (int)(255*c), 
            (int)(255*c));
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int gray = data[clampedX] & 0xFF;
        return (0xFF << 24) | (gray << 16) | (gray << 8) | gray;
    }
}