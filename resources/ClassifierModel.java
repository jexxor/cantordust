package resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ClassifierModel {
    private GhidraSrc cantordust;
    public final static String[] classes = {"arm4", "arm7", "ascii_english", "compressed", "java", "mips", "msil", "ones", "png",
                                "powerpc", "sparc_32", "utf_16_english", "x64", "x86", "x86_padding", "zeros", "embedded_image"};
    private NGramModel[] nGramModels = new NGramModel[classes.length];
    private String basePath;
    private int grams;
    private int[] blockClassifications;
    public static int DEFAULT_GRAMS = 4;
    public static int BLOCK_SIZE = 12;



    public ClassifierModel(GhidraSrc cantordust, int grams) {
        basePath = cantordust.getCurrentDirectory() + "resources/templates/";
        this.grams = grams;
        this.cantordust = cantordust;
    }

    public void initialize(){
        for(int i=0; i < classes.length; i++) {
            byte[] data = null;
            try {
                data = Files.readAllBytes((new File(basePath + classes[i] + ".template")).toPath());
            } catch(IOException e) {
                e.printStackTrace();
            }
            if(data == null || data.length == 0) {
                data = new byte[]{0};
            }
            cantordust.cdprint(String.format("generated "+classes[i]+" ngram\n"));
            cantordust.cdprint("My stuff {\ndata: "+data.length+"\n");
            cantordust.cdprint("grams: "+this.grams+"\n}\n");
            nGramModels[i] = new NGramModel(data, this.grams);
        }
        classifyData();
    }

    public int classify(byte[] data, int low, int high) {
        if(nGramModels[0] == null || high <= low) {
            return 0;
        }
        NGramModel model = new NGramModel(data, low, high - low, grams);
        ExponentialNotation p = model.EvaluateClassification(nGramModels[0]);
        int classification = 0;
        int i;
        for(i = 1; i < classes.length; i++) {
            if(nGramModels[i] == null) {
                continue;
            }
            ExponentialNotation p1 = model.EvaluateClassification(nGramModels[i]);
            if(p1.greaterThan(p)) {
                p = p1;
                classification = i;
            }
        }
        return classification;
    }

    public void classifyData() {
        byte[] data = cantordust.getData();
        int blockCount = (data.length + BLOCK_SIZE - 1) / BLOCK_SIZE;
        blockClassifications = new int[blockCount];
        for(int i=0; i < blockClassifications.length; i++) {
            int low = i * BLOCK_SIZE;
            int high = Math.min(data.length, low + BLOCK_SIZE);
            if(high <= low || (high - low) < Math.max(1, grams)) {
                blockClassifications[i] = 0;
                continue;
            }
            blockClassifications[i] = classify(data, low, high);
        }
    }

    public int classAtIndex(int index) {
        if(blockClassifications == null || blockClassifications.length == 0) {
            return 0;
        }
        byte[] data = cantordust.getData();
        if(data == null || data.length == 0) {
            return 0;
        }
        int clampedIndex = Math.max(0, Math.min(index, data.length - 1));
        int blockIndex = clampedIndex / BLOCK_SIZE;
        if(blockIndex < 0 || blockIndex >= blockClassifications.length) {
            return 0;
        }
        return blockClassifications[blockIndex];
    }
}
