package resources;

import java.util.HashMap;

public abstract class ColorSource {
    protected byte[] data;
    protected GhidraSrc cantordust;
    protected HashMap<Byte, Integer> symbol_map;
    protected String type;

    ColorSource(GhidraSrc cantordust, byte[] data/* , block */) {
        this.cantordust = cantordust;
        this.data = data;
        this.symbol_map = new HashMap<Byte, Integer>();
    }

    public boolean isType(String t){
        return this.type != null && this.type.equals(t);
    }

    public void setData(byte[] data){
        this.data = data;
    }

    protected void rebuildSymbolMap() {
        this.symbol_map.clear();
        if(data == null || data.length == 0) {
            return;
        }

        boolean[] present = new boolean[256];
        for(byte b : data) {
            present[b & 0xFF] = true;
        }

        int rank = 0;
        for(int unsignedValue = 0; unsignedValue < 256; unsignedValue++) {
            if(present[unsignedValue]) {
                this.symbol_map.put((byte)unsignedValue, rank);
                rank++;
            }
        }
    }

    public int getLength() {
        return data != null ? data.length : 0;
    }

    public Rgb point(int x) {
        // implement blocksize
        /*
            * if self.block and (self.block[0]<=x<self.block[1]): return self.block[2]
            * else:
            */
        return getPoint(x);
    }

    public abstract  Rgb getPoint(int x);
    }