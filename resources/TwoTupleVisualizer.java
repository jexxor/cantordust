package resources;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class TwoTupleVisualizer extends Visualizer {
    private volatile BufferedImage img;
    private static final int TUPLE_SPACE = 256 * 256;
    private int divisions = 20;
    private ArrayList<int[]> cachedFreqMaps;
    private Popup coordinatePopup;
    private String colorMode = "g";
    private Boolean gradientMode = true;
    private int cycles = 0;
    private final int[] playbackFreqs = new int[TUPLE_SPACE];
    private final int[] touchedPlaybackTupleIndices = new int[TUPLE_SPACE];
    private final int[] aggregateFreqs = new int[TUPLE_SPACE];
    private final int[] touchedAggregateTupleIndices = new int[TUPLE_SPACE];
    private final Object cacheLock = new Object();
    private final Object renderRequestLock = new Object();
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "cantordust-twotuple-render");
        worker.setDaemon(true);
        return worker;
    });
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

        if (img != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.drawImage(img, 0, 0, (int)window.getWidth(), (int)window.getHeight(), this);
            g2.dispose();
        }
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

        renderExecutor.execute(() -> processRenderRequests());
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
            byte[] data = cantordust.getMainInterface().getData();
            dataHolder[0] = data;
            int macroLow = dataMacroSlider.getValue();
            int macroHigh = dataMacroSlider.getUpperValue();
            int sliderLow = Math.max(macroLow, dataMicroSlider.getValue());
            int sliderHigh = Math.min(macroHigh, dataMicroSlider.getUpperValue());
            int low = Math.max(0, Math.min(sliderLow, Math.max(0, data.length - 1)));
            int high = Math.max(low + 1, Math.min(sliderHigh, data.length));
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
        gradientPlot(nextImage, dataHolder[0], bounds[0], bounds[1]);
        SwingUtilities.invokeLater(() -> {
            img = nextImage;
            repaint();
        });
    }

    private int[] countedByteFrequencies(byte[] data, int low, int high) {
        return countedByteFrequencies(data, low, high, 1);
    }

    private int[] countedByteFrequencies(byte[] data, int low, int high, int stride) {
        int[] tuples = new int[TUPLE_SPACE];
        if(data == null || data.length < 2) {
            return tuples;
        }
        int start = Math.max(0, low);
        int endExclusive = Math.max(start, Math.min(high, data.length));
        int step = Math.max(1, stride);
        for(int tupleIdx = start; tupleIdx + 1 < endExclusive; tupleIdx += step) {
            int first = data[tupleIdx] & 0xFF;
            int second = data[tupleIdx + 1] & 0xFF;
            int tupleIndex = (first << 8) | second;
            tuples[tupleIndex] = tuples[tupleIndex] + 1;
        }
        return tuples;
    }

    private void initializeCaches() {
        ArrayList<int[]> newCache = new ArrayList<int[]>();
        byte[] data = cantordust.getMainInterface().getData();
        if(data == null || data.length == 0) {
            synchronized(cacheLock) {
                cachedFreqMaps = newCache;
            }
            return;
        }
        int cachedSize = Math.max(1, data.length / divisions);
        for(int div = 0; div < divisions - 1; div++) {
            newCache.add(countedByteFrequencies(data, div*cachedSize, (div+1)*cachedSize));
        }
        newCache.add(countedByteFrequencies(data, (divisions-1)*cachedSize, data.length));

        synchronized(cacheLock) {
            cachedFreqMaps = newCache;
        }
    }

    private void gradientPlot(BufferedImage image, byte[] data, int low, int high) {
        cycles += 1;
        int[] pixelBuffer = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        if(data == null || data.length < 2) {
            return;
        }
        boolean interactiveScrub = dataRangeSlider != null && dataRangeSlider.getValueIsAdjusting();
        if(mainInterface.isPlaybackActive() || interactiveScrub) {
            int range = Math.max(1, high - low);
            int stride = Math.max(1, range / 65536);
            int touchedCount = 0;
            int start = Math.max(0, low);
            int end = Math.min(high, data.length - 1);
            for(int tupleIdx = start; tupleIdx < end; tupleIdx += stride) {
                int first = data[tupleIdx] & 0xFF;
                int second = data[tupleIdx + 1] & 0xFF;
                int tupleIndex = (first << 8) | second;
                if(playbackFreqs[tupleIndex] == 0) {
                    touchedPlaybackTupleIndices[touchedCount++] = tupleIndex;
                }
                playbackFreqs[tupleIndex] = playbackFreqs[tupleIndex] + 1;
            }
            for(int touchedIdx = 0; touchedIdx < touchedCount; touchedIdx++) {
                int tupleIndex = touchedPlaybackTupleIndices[touchedIdx];
                int freq = playbackFreqs[tupleIndex];
                int x = (tupleIndex >> 8) & 0xFF;
                int y = tupleIndex & 0xFF;
                setTuplePixel(pixelBuffer, x, y, getColorArgb(freq, colorMode));
                playbackFreqs[tupleIndex] = 0;
            }
            return;
        }

        int cachedSize = Math.max(1, data.length / divisions);
        int touchedCount = 0;
        ArrayList<int[]> cacheSnapshot;
        synchronized(cacheLock) {
            cacheSnapshot = cachedFreqMaps == null ? new ArrayList<int[]>() : new ArrayList<int[]>(cachedFreqMaps);
        }

        int start = Math.max(0, Math.min(low, data.length));
        int end = Math.max(start, Math.min(high, data.length));
        int firstAligned = ((start + cachedSize - 1) / cachedSize) * cachedSize;

        int prefixEnd = Math.min(end, firstAligned);
        if(prefixEnd > start) {
            int[] prefixFreqs = countedByteFrequencies(data, start, prefixEnd);
            touchedCount = mergeFreqCounts(prefixFreqs, aggregateFreqs, touchedAggregateTupleIndices, touchedCount);
        }

        int blockStart = firstAligned;
        while(blockStart + cachedSize <= end) {
            int blockIndex = blockStart / cachedSize;
            if(blockIndex >= 0 && blockIndex < cacheSnapshot.size()) {
                touchedCount = mergeFreqCounts(cacheSnapshot.get(blockIndex), aggregateFreqs, touchedAggregateTupleIndices, touchedCount);
            } else {
                int[] blockFreqs = countedByteFrequencies(data, blockStart, blockStart + cachedSize);
                touchedCount = mergeFreqCounts(blockFreqs, aggregateFreqs, touchedAggregateTupleIndices, touchedCount);
            }
            blockStart += cachedSize;
        }

        if(blockStart < end) {
            int[] suffixFreqs = countedByteFrequencies(data, blockStart, end);
            touchedCount = mergeFreqCounts(suffixFreqs, aggregateFreqs, touchedAggregateTupleIndices, touchedCount);
        }

        for(int touchedIdx = 0; touchedIdx < touchedCount; touchedIdx++) {
            int tupleIndex = touchedAggregateTupleIndices[touchedIdx];
            int freq = aggregateFreqs[tupleIndex];
            int x = (tupleIndex >> 8) & 0xFF;
            int y = tupleIndex & 0xFF;
            setTuplePixel(pixelBuffer, x, y, getColorArgb(freq, colorMode));
            aggregateFreqs[tupleIndex] = 0;
        }
    }

    private int mergeFreqCounts(int[] sender, int[] reciever, int[] touchedIndices, int touchedCount) {
        for(int tupleIndex = 0; tupleIndex < sender.length; tupleIndex++) {
            int value = sender[tupleIndex];
            if(value == 0) {
                continue;
            }
            if(reciever[tupleIndex] == 0) {
                touchedIndices[touchedCount++] = tupleIndex;
            }
            reciever[tupleIndex] = reciever[tupleIndex] + value;
        }
        return touchedCount;
    }

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

    private int getColorArgb(int freq, String rgbPosition) {
        int colorStep = 5;
        int min = 10;
        int channelValue = min + (freq * colorStep > 255 - min ? 255 - min : freq * colorStep);
        switch(rgbPosition) {
            case "r":
                if(gradientMode)
                    return (0xFF << 24) | (channelValue << 16);
                else {
                    return 0xFFFF0000;
                }
            case "g":
                if(gradientMode) {
                    return (0xFF << 24) | (channelValue << 8);
                } else {
                    return 0xFF00FF00;
                }
            case "b":
                if(gradientMode) {
                    return (0xFF << 24) | channelValue;
                } else {
                    return 0xFF0000FF;
                }
            default:
                return 0xFF00FF00;
        }
    }

    private void setTuplePixel(int[] pixelBuffer, int firstByte, int secondByte, int argb) {
        int row = firstByte & 0xFF;
        int col = secondByte & 0xFF;
        int pixelIndex = row * 256 + col;
        if(pixelIndex < 0 || pixelIndex >= pixelBuffer.length) {
            return;
        }
        pixelBuffer[pixelIndex] = argb;
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
