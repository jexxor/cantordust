package resources;

public class ColorClass extends ColorSource {
    public ColorClass(GhidraSrc cantordust, byte[] data) {
        super(cantordust, data);
        this.type = "class";
    }

    @Override
    public Rgb getPoint(int x) {
        if(data == null || data.length == 0) {
            return new Rgb(0, 0, 0);
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int c = this.data[clampedX] & 0xFF;
        if(c == 0){
            return new Rgb(0, 0, 0);
        } else if(c == 255){
            return new Rgb(255, 255, 255);
        } else if (c >= 32 && c < 127){
            return new Rgb(55, 126, 184);
        } else { return new Rgb(228, 26, 28); }
    }

    @Override
    public int pointArgb(int x) {
        if(data == null || data.length == 0) {
            return 0xFF000000;
        }
        int clampedX = Math.max(0, Math.min(x, data.length - 1));
        int c = this.data[clampedX] & 0xFF;
        if(c == 0) {
            return 0xFF000000;
        } else if(c == 255) {
            return 0xFFFFFFFF;
        } else if(c >= 32 && c < 127) {
            return (0xFF << 24) | (55 << 16) | (126 << 8) | 184;
        }
        return (0xFF << 24) | (228 << 16) | (26 << 8) | 28;
    }
}