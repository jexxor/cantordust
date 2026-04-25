package resources;

// utils
import java.lang.Error;

public class Utils{
    protected GhidraSrc cantordust;
    protected byte[] data;
    public Utils(GhidraSrc cantordust){
        this.cantordust = cantordust;
        data = this.cantordust.getData();
    }
    public double entropy(byte[] data, int blocksize, int offset, int symbols) {
        if(data == null || data.length == 0 || blocksize <= 0) {
            return 0.0;
        }

        int effectiveBlockSize = Math.max(1, Math.min(blocksize, data.length));
        int clampedOffset = Math.max(0, Math.min(offset, data.length - 1));
        int start = clampedOffset - (effectiveBlockSize / 2);
        start = Math.max(0, Math.min(start, data.length - effectiveBlockSize));
        int end = Math.min(data.length, start + effectiveBlockSize);
        int sampleCount = end - start;
        if(sampleCount <= 1) {
            return 0.0;
        }

        int[] counts = new int[256];
        for(int i = start; i < end; i++){
            counts[data[i] & 0xFF]++;
        }
        int base = Math.max(2, Math.min(sampleCount, symbols));
        double entropy = 0;
        for(int count : counts) {
            if(count == 0) {
                continue;
            }
            double p = count / (double)sampleCount;
            double log = (double)Math.log(p)/(double)Math.log(base);
            entropy += (p * log);
        }
        return -entropy;
    }
    public int graycode(int x){
        return x^(x>>>1);
    }
    public int igraycode(int x){
        if(x==0){
            return x;
        }
        int m = (int)(Math.ceil(Math.log(x)/Math.log(2)))+1;
        int i = x;
        int j = 1;
        while(j < m){
            i = i ^ (x>>>j);
            j+=1;
        }
        return i;
    }
    public int rrot(int x, int i, int width){
        /*
            Right bit-rotation.
            width: the bit width of x.
        */
        assert x < (int)(Math.pow(2, width));
        i = i%width;
        x = (x>>>i) | (x<<width-i);
        return x&(int)(Math.pow(2, width)-1);
    }
    public int lrot(int x, int i, int width){
        /*
            Left bit-rotation.
            width: the bit width of x.
        */
        assert x < Math.pow(2, width);
        i = i%width;
        x = (x<<i) | (x>>>width-i);
        return x&((int)Math.pow(2, width)-1);
    }
    public int tsb(int x, int width){
        /*
            Tailing set bits
        */
        assert x < (int)Math.pow(2, width);
        int i = 0;
        while((x&1)==1 && i<=width){
            x = x >>> 1;
            i+=1;
        }
        return i;
    }
    public int setbit(int x, int w, int i, int b){
        /*
            Sets bit i in an integer x of width w to b.
            b must be 1 or 0
        */
        assert b==1 || b==0;
        assert i<w;
        if(b==1){
            return x | (int)Math.pow(2, w-i-1);
        } else {
            return x & ~(int)Math.pow(2, w-i-1);
        }
    }
    public int bitrange(int x, int width, int start, int end){
        /*
            Extract a bit range as an integer.
            (start, end) is inclusive lower bound, exclusive upper bound.
        */
        return x >>> (width-end) & (int)(Math.pow(2, end-start)-1);
    }
}