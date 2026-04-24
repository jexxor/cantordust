package resources;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class TwoTupleVisualizer extends Visualizer {
    private volatile BufferedImage img;
    private int divisions = 20;
    private ArrayList<HashMap<TwoByteTuple, Integer>> cachedFreqMaps;
    HashSet<TwoByteTuple> existingTuples;
    private Popup coordinatePopup;
    private String colorMode = "g";
    private Boolean gradientMode = true;
    private int cycles = 0;
    private final Object cacheLock = new Object();
    private final Object renderRequestLock = new Object();
    private boolean renderWorkerRunning = false;
    private int pendingRenderGeneration = 0;

    public TwoTupleVisualizer(int windowSize, GhidraSrc cantordust, JFrame frame) {
        super(windowSize, cantordust);
        this.img = null;
        initializeCaches();
        constructImageAsync();
        addChangeListeners();
        createPopupMenu(frame);
    }

    // Special constructor for initialization of plugin
    public TwoTupleVisualizer(int windowSize, GhidraSrc cantordust, MainInterface mainInterface, JFrame frame) {
        super(windowSize, cantordust, mainInterface);
        this.img = null;
        initializeCaches();
        constructImageAsync();
        addChangeListeners();
        createPopupMenu(frame);
    }

    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        Rectangle window = getVisibleRect();

        if (img != null)
            g.drawImage(img, 0, 0, (int)window.getWidth(), (int)window.getHeight(), this);
    }

    public void constructImageAsync() {
        if(!isShowing() && mainInterface.currVis != this && img != null) {
            return;
        }
        synchronized(renderRequestLock) {
            pendingRenderGeneration++;
            if(renderWorkerRunning) {
                return;
            }
            renderWorkerRunning = true;
        }

        Thread worker = new Thread(() -> processRenderRequests(), "cantordust-twotuple-render");
        worker.setDaemon(true);
        worker.start();
    }

    private void processRenderRequests() {
        while(true) {
            int generation;
            synchronized(renderRequestLock) {
                generation = pendingRenderGeneration;
            }

            constructImage();

            synchronized(renderRequestLock) {
                if(generation == pendingRenderGeneration) {
                    renderWorkerRunning = false;
                    return;
                }
            }
        }
    }

    private void constructImage() {
        final int[] bounds = new int[2];
        final byte[][] dataHolder = new byte[1][];
        Runnable captureState = () -> {
            dataMicroSlider.setMinimum(dataMacroSlider.getValue());
            dataMicroSlider.setMaximum(dataMacroSlider.getUpperValue());
            byte[] data = cantordust.getMainInterface().getData();
            dataHolder[0] = data;
            int low = Math.max(0, Math.min(dataMicroSlider.getValue(), Math.max(0, data.length - 1)));
            int high = Math.max(low + 1, Math.min(dataMicroSlider.getUpperValue(), data.length));
            bounds[0] = low;
            bounds[1] = high;
        };

        try {
            if(SwingUtilities.isEventDispatchThread()) {
                captureState.run();
            } else {
                SwingUtilities.invokeAndWait(captureState);
            }
        } catch (Exception ignored) {
            return;
        }

        if(dataHolder[0] == null || dataHolder[0].length == 0) {
            return;
        }

        BufferedImage nextImage = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        gradientPlot(nextImage.createGraphics(), bounds[0], bounds[1]);
        SwingUtilities.invokeLater(() -> {
            img = nextImage;
            repaint();
        });
    }

    private HashMap<TwoByteTuple, Integer> countedByteFrequencies(int low, int high) {
        return countedByteFrequencies(low, high, 1);
    }

    private HashMap<TwoByteTuple, Integer> countedByteFrequencies(int low, int high, int stride) {
        // data needs fixed for large file sizes
        byte[] data = cantordust.getMainInterface().getData();
        HashMap<TwoByteTuple, Integer> tuples = new HashMap<>();
        int start = Math.max(0, low);
        int end = Math.min(high, data.length);
        int step = Math.max(1, stride);
        for(int tupleIdx = start; tupleIdx < end - 1; tupleIdx += step) {
            TwoByteTuple tuple = new TwoByteTuple(data[tupleIdx], data[tupleIdx+1]);
            Integer freq = tuples.get(tuple);
            if(freq != null) {
                tuples.put(tuple, freq + 1);
            } else {
                tuples.put(tuple,  1);
            }
        }
        return tuples;
    }

    private void initializeCaches() {
        ArrayList<HashMap<TwoByteTuple, Integer>> newCache = new ArrayList<HashMap<TwoByteTuple, Integer>>();
        HashSet<TwoByteTuple> newExistingTuples = new HashSet<TwoByteTuple>();
        // data needs fixed for large file sizes
        byte[] data = cantordust.getMainInterface().getData();
        int cachedSize = Math.max(1, data.length / divisions);
        for(int div = 0; div < divisions - 1; div++) {
            newCache.add(countedByteFrequencies(div*cachedSize, (div+1)*cachedSize));
        }
        newCache.add(countedByteFrequencies((divisions-1)*cachedSize, data.length));

        synchronized(cacheLock) {
            cachedFreqMaps = newCache;
            existingTuples = newExistingTuples;
        }
    }

    private void gradientPlot(Graphics2D g, int low, int high) {
        cycles += 1;
        boolean interactiveScrub = dataRangeSlider != null && dataRangeSlider.getValueIsAdjusting();
        if(mainInterface.isPlaybackActive() || interactiveScrub) {
            byte[] data = cantordust.getMainInterface().getData();
            int range = Math.max(1, high - low);
            int stride = Math.max(1, range / 65536);
            int[] playbackFreqs = new int[256 * 256];
            int start = Math.max(0, low);
            int end = Math.min(high, data.length - 1);
            for(int tupleIdx = start; tupleIdx < end; tupleIdx += stride) {
                int first = data[tupleIdx] & 0xFF;
                int second = data[tupleIdx + 1] & 0xFF;
                int tupleIndex = (first << 8) | second;
                playbackFreqs[tupleIndex] = playbackFreqs[tupleIndex] + 1;
            }
            for(int tupleIndex = 0; tupleIndex < playbackFreqs.length; tupleIndex++) {
                int freq = playbackFreqs[tupleIndex];
                if(freq == 0) {
                    continue;
                }
                g.setColor(getColor(freq, colorMode));
                int x = (tupleIndex >> 8) & 0xFF;
                int y = tupleIndex & 0xFF;
                g.fillRect(y, x, 1, 1);
            }
            g.dispose();
            return;
        }

        // data needs fixed for large file sizes
        int cachedSize = Math.max(1, this.cantordust.getMainInterface().getData().length / divisions);
        HashMap<TwoByteTuple, Integer> totalFreqs = new HashMap<>();
        HashMap<TwoByteTuple, Integer> leftStraggler = null;
        HashMap<TwoByteTuple, Integer> rightStraggler = null;
        ArrayList<HashMap<TwoByteTuple, Integer>> cacheSnapshot;
        synchronized(cacheLock) {
            cacheSnapshot = cachedFreqMaps == null ? new ArrayList<HashMap<TwoByteTuple, Integer>>() : new ArrayList<HashMap<TwoByteTuple, Integer>>(cachedFreqMaps);
        }
        int firstCacheBlockStart = nextBlock(low, cachedSize);
        int lastCacheBlockEnd = lastBlock(high, cachedSize);
        if(firstCacheBlockStart != low) {
            leftStraggler = countedByteFrequencies(low, firstCacheBlockStart-1);
            for(TwoByteTuple tuple: leftStraggler.keySet()) {
                if(totalFreqs.containsKey(tuple)) {
                    totalFreqs.put(tuple, leftStraggler.get(tuple) + totalFreqs.get(tuple));
                } else {
                    totalFreqs.put(tuple, leftStraggler.get(tuple));
                }
            }
        }
        for(int currentBlock = firstCacheBlockStart / cachedSize; currentBlock <= lastCacheBlockEnd / cachedSize; currentBlock++) {
            int cacheIndex = currentBlock - 1;
            if(cacheIndex >= 0 && cacheIndex < cacheSnapshot.size()) {
                mergeFreqCounts(cacheSnapshot.get(cacheIndex), totalFreqs);
            }
        }

        int colorStep = 5;
        int min = 0;
        for(TwoByteTuple twoTuple: totalFreqs.keySet()) {
            int freq = totalFreqs.get(twoTuple);
            //g.setColor(new Color(0, min + (freq*colorStep > 255 - min ? 255 - min : freq*colorStep), 0));
            g.setColor(getColor(freq, colorMode));
            //int colorVal = min + (freq*colorStep > 255 - min ? 255 - min : freq*colorStep);
            //g.setColor(new Color(colorVal, colorVal, colorVal));
            int x = twoTuple.x & 0xff;
            int y = twoTuple.y & 0xff;
            g.fillRect(y, x, 1, 1);

        }
        g.dispose();
    }

    private void mergeFreqCounts(HashMap<TwoByteTuple, Integer> sender, HashMap<TwoByteTuple, Integer> reciever) {
        for(TwoByteTuple tuple: sender.keySet()) {
            reciever.put(tuple, sender.get(tuple) + (reciever.containsKey(tuple) ? reciever.get(tuple) : 0));
        }
    }

    private int nextBlock(int x, int size) {
        if (x % size == 0) {
            return x;
        } else if (x < size) {
            return size;
        } else {
            return Math.floorDiv(x, size)*size + size;
        }
    }

    private int lastBlock(int x, int size) {
        return x - (x % size);
    }
        /*int colorStep = 10;
        int min = 60;
        for(TwoByteTuple twoTuple: tuples.keySet()) {
            freq = tuples.get(twoTuple);
            g.setColor(new Color(0, min + (freq*colorStep > 255 - min ? 255 - min : freq*colorStep), 0));
            //int colorVal = min + (freq*colorStep > 255 - min ? 255 - min : freq*colorStep);
            //g.setColor(new Color(colorVal, colorVal, colorVal));
            int x = twoTuple.x & 0xff;
            int y = twoTuple.y & 0xff;
            g.fill(new Rectangle2D.Double(x, y, 1, 1));
        }
    }*/

    public static int getWindowSize() {
        return 500;
    }

    private void addChangeListeners() {
        dataMacroSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                constructImageAsync();
            }
        });
        dataMicroSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                constructImageAsync();
            }
        });
        if(dataRangeSlider != null) {
            dataRangeSlider.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    if(!dataRangeSlider.getValueIsAdjusting() && !mainInterface.isPlaybackActive()) {
                        initializeCaches();
                    }
                    constructImageAsync();
                }
            });
        }
    }

    private Color getColor(int freq, String rgbPosition) {
        int colorStep = 5;
        int min = 10;
        switch(rgbPosition) {
            case "r":
                if(gradientMode)
                    return new Color(min + (freq*colorStep > 255 - min ? 255 - min : freq*colorStep), 0, 0);
                else {
                    return Color.RED;
                }
            case "g":
                if(gradientMode) {
                    return new Color(0, min + (freq * colorStep > 255 - min ? 255 - min : freq * colorStep), 0);
                } else {
                    return Color.GREEN;
                }
            case "b":
                if(gradientMode) {
                    return new Color(0, 0, min + (freq * colorStep > 255 - min ? 255 - min : freq * colorStep));
                } else {
                    return Color.BLUE;
                }
            default:
                return null;
        }
    }

    public void createPopupMenu(JFrame frame){
        JPopupMenu popup = new JPopupMenu("colors");
        JMenuItem redItem = new JMenuItem("red");
        redItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                colorMode = "r";
                constructImageAsync();
            }
        });
        popup.add(redItem);

        JMenuItem greenItem = new JMenuItem("green");
        greenItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                colorMode = "g";
                constructImageAsync();
            }
        });
        popup.add(greenItem);

        JMenuItem blueItem = new JMenuItem("blue");
        blueItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                colorMode = "b";
                constructImageAsync();
            }
        });
        popup.add(blueItem);


        JMenuItem gradientToggle = new JMenuItem("toggle gradient");
        gradientToggle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                gradientMode = !gradientMode;
                constructImageAsync();
            }
        });
        popup.add(gradientToggle);

        this.addMouseListener(new MouseAdapter() {
            public void mouseReleased(MouseEvent e) {
                if(e.getButton() == MouseEvent.BUTTON1) {
                    showCoordinatePopup(e);
                } else if(e.getButton() == MouseEvent.BUTTON3){
                    popup.show(frame, TwoTupleVisualizer.this.getX() + e.getX(), TwoTupleVisualizer.this.getY() + e.getY());
                }
            }
        });

        this.add(popup);
    }

    private void showCoordinatePopup(MouseEvent e) {
        int x = (int)Math.floor((e.getX() * 256.0) / Math.max(1, getWidth()));
        int y = (int)Math.floor((e.getY() * 256.0) / Math.max(1, getHeight()));
        x = Math.max(0, Math.min(255, x));
        y = Math.max(0, Math.min(255, y));

        if(coordinatePopup != null) {
            coordinatePopup.hide();
            coordinatePopup = null;
        }

        JLabel label = new JLabel(String.format("(0x%02X, 0x%02X)", x, y));
        JPanel panel = new JPanel();
        panel.add(label);
        if(mainInterface.theme == 1) {
            panel.setBackground(Color.black);
            label.setForeground(Color.white);
        }

        coordinatePopup = PopupFactory.getSharedInstance().getPopup(TwoTupleVisualizer.this, panel, e.getXOnScreen() + 8, e.getYOnScreen() + 8);
        coordinatePopup.show();

        Timer popupTimer = new Timer(1100, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                if(coordinatePopup != null) {
                    coordinatePopup.hide();
                    coordinatePopup = null;
                }
            }
        });
        popupTimer.setRepeats(false);
        popupTimer.start();
    }

}
