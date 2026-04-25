package resources;

// metricMap
import javax.swing.*;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.*;
import java.awt.image.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

public class MetricMap extends Visualizer{
    private static final Set<MetricMap> LIVE_INSTANCES = Collections.newSetFromMap(new ConcurrentHashMap<MetricMap, Boolean>());
    protected byte[] data;
    protected static int size_hilbert = 512;
    private static final long GUIDE_FOCUS_UPDATE_THROTTLE_MS = 16L;
    private static final int SELECTION_DRAG_THRESHOLD_PX = 6;
    private static final byte[] EMPTY_BYTES = new byte[0];
    private byte[] reusableCurrentDataBuffer = new byte[0];
    protected int[][] pixelMap2D;
    protected int[] pixelMap1D;
    private volatile int memLocBaseOffset = 0;
    private volatile int renderSourceLength = 1;
    private volatile int renderMapLength = 1;
    private volatile long lastRenderPublishMs = 0L;
    private volatile Scurve renderLookupMap;
    private volatile BufferedImage renderedImage;
    private volatile BufferedImage renderedGuideOverlay;
    private volatile PointCache cachedPointCache;
    private volatile SourceOffsetCache cachedSourceOffsetCache;
    private final Object pointCacheLock = new Object();
    private final Object sourceOffsetCacheLock = new Object();
    private BufferedImage cachedGuideOverlay;
    private String cachedGuideOverlayKey;
    private boolean guidePathEnabled = false;
    private int guideTrailLength = 1536;
    private int guideFocusIndex = -1;
    private int lastGuideFocusRenderedIndex = -1;
    private long lastGuideFocusUpdateMs = 0L;
    protected Popup popupAddr;
    private Popup popupSelection;
    private boolean selectionDragActive = false;
    private int selectionStartViewX = -1;
    private int selectionStartViewY = -1;
    private int selectionCurrentViewX = -1;
    private int selectionCurrentViewY = -1;
    private boolean selectionArmed = false;
    private boolean primaryPressActive = false;
    private int primaryPressViewX = -1;
    private int primaryPressViewY = -1;
    protected JPopupMenu popupMenu;
    protected Scurve map;
    protected JPanel panel = new JPanel();
    protected String type_plot = "square";
    protected ColorSource csource;
    private JSlider dataWidthSlider;
    private final Object drawMonitor = new Object();
    private final ExecutorService drawExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "cantordust-metric-map-render");
        worker.setDaemon(true);
        return worker;
    });
    private boolean drawInProgress = false;
    private boolean redrawRequested = false;
    private volatile long drawRequestVersion = 0L;
    private boolean isClassifier = false;

    public MetricMap(int windowSize, GhidraSrc cantordust, JFrame frame, Boolean isCurrentView) {
        super(windowSize, cantordust);
        registerLiveInstance();
        data = this.cantordust.getMainInterface().getData();
        dataWidthSlider = this.cantordust.getMainInterface().widthSlider;
        createPopupMenu(frame);
        sliderConfig();
        mouseConfig(frame, isCurrentView);
        this.csource = new ColorEntropy(this.cantordust, getCurrentData());
        this.map = new Hilbert(this.cantordust, 2, (int)(Math.log(getWindowSize())/Math.log(2)));
        this.renderLookupMap = this.map;
        draw();
    }
    
    // Special constructor for initialization of plugin
    public MetricMap(int windowSize, GhidraSrc cantordust, MainInterface mainInterface, JFrame frame, Boolean isCurrentView) {
        super(windowSize, cantordust, mainInterface);
        registerLiveInstance();
        data = mainInterface.getData();
        dataWidthSlider = mainInterface.widthSlider;
        createPopupMenu(frame);
        sliderConfig();
        mouseConfig(frame, isCurrentView);
        this.csource = new ColorEntropy(this.cantordust, getCurrentData());
        this.map = new Hilbert(this.cantordust, 2, (int)(Math.log(getWindowSize())/Math.log(2)));
        this.renderLookupMap = this.map;
        draw();
    }

    private void registerLiveInstance() {
        LIVE_INSTANCES.add(this);
        addHierarchyListener(event -> {
            if((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0 && !isDisplayable()) {
                LIVE_INSTANCES.remove(MetricMap.this);
            }
        });
    }

    public static void requestRenderAll() {
        for(MetricMap metricMap : LIVE_INSTANCES) {
            if(metricMap != null) {
                metricMap.requestRender();
            }
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        if(renderedImage != null) {
            RenderSettings.applyImageRenderingHints(g2);
            g2.drawImage(renderedImage, 0, 0, getWidth(), getHeight(), null);
        }
        if(guidePathEnabled && renderedGuideOverlay != null) {
            g2.drawImage(renderedGuideOverlay, 0, 0, getWidth(), getHeight(), null);
        }
        if(selectionDragActive && selectionStartViewX >= 0 && selectionStartViewY >= 0
                && selectionCurrentViewX >= 0 && selectionCurrentViewY >= 0) {
            int left = Math.min(selectionStartViewX, selectionCurrentViewX);
            int top = Math.min(selectionStartViewY, selectionCurrentViewY);
            int width = Math.abs(selectionCurrentViewX - selectionStartViewX);
            int height = Math.abs(selectionCurrentViewY - selectionStartViewY);
            g2.setColor(new Color(80, 170, 255, 55));
            g2.fillRect(left, top, width, height);
            g2.setColor(new Color(80, 170, 255, 220));
            g2.drawRect(left, top, Math.max(1, width), Math.max(1, height));
        }
        g2.dispose();
    }

    public void sliderConfig(){
        this.dataMicroSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                draw();
            }
        });
        this.dataMacroSlider.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent changeEvent) {
                draw();
            }
        });
        if(dataRangeSlider != null){
            this.dataRangeSlider.addChangeListener(new ChangeListener() {
                @Override
                public void stateChanged(ChangeEvent changeEvent) {
                    data = cantordust.getMainInterface().getData();
                    draw();
                }
            });
        }
        dataWidthSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                if(!dataWidthSlider.getValueIsAdjusting()) {
                    if(map.isType("linear")){
                        cantordust.cdprint("in linear, sliding\n");
                        cantordust.cdprint("ch:"+dataWidthSlider.getValue()+"\n");
                        int change = dataWidthSlider.getValue();
                        if(change < size_hilbert){
                            cantordust.cdprint("less\n");
                            int inc = (size_hilbert-change)*2;
                            change = size_hilbert+inc;
                            map.setWidth(change);
                            map.setHeight(size_hilbert);
                        }else if(change > size_hilbert){
                            cantordust.cdprint("more\n");
                            int inc = (change-size_hilbert)*2;
                            change = size_hilbert+inc;
                            map.setHeight(change);
                            map.setWidth(size_hilbert);
                        }
                        else{
                            map.setWidth(size_hilbert);
                            map.setHeight(size_hilbert);
                        }
                        draw();
                    }
                }
            }
        });
    }

    public void mouseConfig(JFrame frame, boolean isCurrentView){
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                // cantordust.cdprint("dragged\n");
                int b1 = MouseEvent.BUTTON1_DOWN_MASK;
                int b2 = MouseEvent.BUTTON2_DOWN_MASK;
                if ((e.getModifiersEx() & (b1 | b2)) == b1) {
                    if(selectionArmed) {
                        if(selectionDragActive) {
                            updateSelection(e);
                            return;
                        }
                        if(primaryPressActive && hasMovedEnoughForSelection(e.getX(), e.getY())) {
                            beginSelection(primaryPressViewX, primaryPressViewY);
                            updateSelection(e);
                        }
                        return;
                    }
                    if(guidePathEnabled) {
                        updateGuideFocusFromPointer(e, false);
                    }
                    hideAddressPopup();
                    if(e.getX() < getWidth() && e.getY() < getHeight()){
                        if(e.getX() >= 0 && e.getY() >= 0){
                            mousePressed(e);
                        }
                    }
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if(guidePathEnabled) {
                    updateGuideFocusFromPointer(e, false);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                MouseListener[] mA = getMouseListeners();
                if(mA.length >= 1) {
                    mA[0].mousePressed(e);
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int b1 = MouseEvent.BUTTON1_DOWN_MASK;
                int b2 = MouseEvent.BUTTON2_DOWN_MASK;
                if ((e.getModifiersEx() & (b1 | b2)) == b1) {
                    if(e.getID() == MouseEvent.MOUSE_PRESSED) {
                        primaryPressActive = true;
                        primaryPressViewX = clampViewX(e.getX());
                        primaryPressViewY = clampViewY(e.getY());
                        selectionArmed = isSelectionGesture(e);
                    }
                    if(selectionArmed) {
                        hideAddressPopup();
                        hideSelectionPopup();
                        return;
                    }
                    JPanel bv = MetricMap.this;
                    JFrame metricMap = frame;
                    int x_point = scaleToMapX(e.getX());
                    int y_point = scaleToMapY(e.getY());
                    int xf = metricMap.getX() + e.getX();
                    int yf = metricMap.getY() + e.getY();
                    if(isCurrentView){
                        xf = (int)bv.getLocationOnScreen().getX() + e.getX();
                        yf = (int)bv.getLocationOnScreen().getY() + e.getY() - 26;
                    }
                    TwoIntegerTuple p = new TwoIntegerTuple(x_point, y_point);
                    Scurve lookupMap = renderLookupMap != null ? renderLookupMap : map;
                    if(lookupMap == null) {
                        return;
                    }
                    int loc = lookupMap.index(p);
                    updateGuideFocus(lookupMap, loc, e.getID() == MouseEvent.MOUSE_PRESSED);
                    int relativeLocation = mapIndexToRelativeLocation(loc);
                    int memoryLocation = memLocBaseOffset + relativeLocation;
                    long minGhidraAddress = Long.parseLong(cantordust.getCurrentProgram().getMinAddress().toString(false), 16);
                    String currentAddress = Long.toHexString(minGhidraAddress+(long)memoryLocation).toUpperCase();
                    JLabel l;
                    if(isClassifier) {
                        ClassifierModel classifier = cantordust.getClassifier();
                        if(classifier != null) {
                            int classIndex = 0;
                            try {
                                classIndex = classifier.classAtIndex(memoryLocation);
                            } catch(RuntimeException ignored) {
                                classIndex = 0;
                            }
                            classIndex = Math.max(0, Math.min(ClassifierModel.classes.length - 1, classIndex));
                            l = new JLabel(ClassifierModel.classes[classIndex]);
                        } else {
                            l = new JLabel(currentAddress);
                        }
                    } else {
                        l = new JLabel(currentAddress);
                    }
                    PopupFactory pf = new PopupFactory(); 
                    JPanel p2 = new JPanel();
                    if(mainInterface.theme == 1) {
                        l.setForeground(Color.white);
                        p2.setBackground(Color.black);
                    }
                    p2.add(l);
                    popupAddr = pf.getPopup(metricMap, p2, xf, yf);
                    popupAddr.show();
                    
                    try{
                        // Set current location in Ghidra to this address
                        cantordust.gotoFileAddress(memoryLocation);
                    } catch(IllegalArgumentException exception){
                    }
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if(selectionDragActive) {
                    completeSelection(e);
                    primaryPressActive = false;
                    primaryPressViewX = -1;
                    primaryPressViewY = -1;
                    selectionArmed = false;
                    return;
                }
                primaryPressActive = false;
                primaryPressViewX = -1;
                primaryPressViewY = -1;
                selectionArmed = false;
                hideAddressPopup();
                if(e.getButton() == 3){
                    popupMenu.show(frame, MetricMap.this.getX() + e.getX(), MetricMap.this.getY() + e.getY());
                }
            }
        });
    }

    private int scaleToMapX(int viewX) {
        Scurve lookupMap = renderLookupMap != null ? renderLookupMap : map;
        int mapWidth = lookupMap != null ? lookupMap.dimensions().get(0) : size_hilbert;
        return Math.max(0, Math.min(mapWidth - 1, (int)Math.floor((viewX * (double)mapWidth) / Math.max(1, getWidth()))));
    }

    private int scaleToMapY(int viewY) {
        Scurve lookupMap = renderLookupMap != null ? renderLookupMap : map;
        int mapHeight = lookupMap != null ? lookupMap.dimensions().get(1) : size_hilbert;
        return Math.max(0, Math.min(mapHeight - 1, (int)Math.floor((viewY * (double)mapHeight) / Math.max(1, getHeight()))));
    }

    private int mapIndexToRelativeLocation(int mapIndex) {
        int sourceLength = Math.max(1, renderSourceLength);
        int mapLength = Math.max(1, renderMapLength);
        int clampedMapIndex = Math.max(0, Math.min(mapLength - 1, mapIndex));
        int relativeLocation = (int)(((long)clampedMapIndex * (long)sourceLength) / (long)mapLength);
        if(relativeLocation >= sourceLength) {
            relativeLocation = sourceLength - 1;
        }
        return Math.max(0, relativeLocation);
    }

    private int clampViewX(int x) {
        return Math.max(0, Math.min(Math.max(0, getWidth() - 1), x));
    }

    private int clampViewY(int y) {
        return Math.max(0, Math.min(Math.max(0, getHeight() - 1), y));
    }

    private boolean isSelectionGesture(MouseEvent e) {
        return (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK) != 0;
    }

    private boolean hasMovedEnoughForSelection(int viewX, int viewY) {
        if(!primaryPressActive) {
            return false;
        }
        int dx = Math.abs(clampViewX(viewX) - primaryPressViewX);
        int dy = Math.abs(clampViewY(viewY) - primaryPressViewY);
        return Math.max(dx, dy) >= SELECTION_DRAG_THRESHOLD_PX;
    }

    private void hideAddressPopup() {
        if(popupAddr != null) {
            popupAddr.hide();
            popupAddr = null;
        }
    }

    private void hideSelectionPopup() {
        if(popupSelection != null) {
            popupSelection.hide();
            popupSelection = null;
        }
    }

    private void beginSelection(int startViewX, int startViewY) {
        hideAddressPopup();
        hideSelectionPopup();
        selectionDragActive = true;
        selectionStartViewX = clampViewX(startViewX);
        selectionStartViewY = clampViewY(startViewY);
        selectionCurrentViewX = selectionStartViewX;
        selectionCurrentViewY = selectionStartViewY;
        repaint();
    }

    private void updateSelection(MouseEvent e) {
        if(!selectionDragActive) {
            return;
        }
        selectionCurrentViewX = clampViewX(e.getX());
        selectionCurrentViewY = clampViewY(e.getY());
        repaint();
    }

    private void resetSelection() {
        selectionDragActive = false;
        selectionStartViewX = -1;
        selectionStartViewY = -1;
        selectionCurrentViewX = -1;
        selectionCurrentViewY = -1;
    }

    private void completeSelection(MouseEvent e) {
        if(!selectionDragActive) {
            return;
        }
        updateSelection(e);

        int left = Math.min(selectionStartViewX, selectionCurrentViewX);
        int right = Math.max(selectionStartViewX, selectionCurrentViewX);
        int top = Math.min(selectionStartViewY, selectionCurrentViewY);
        int bottom = Math.max(selectionStartViewY, selectionCurrentViewY);

        int minMapX = Math.min(scaleToMapX(left), scaleToMapX(right));
        int maxMapX = Math.max(scaleToMapX(left), scaleToMapX(right));
        int minMapY = Math.min(scaleToMapY(top), scaleToMapY(bottom));
        int maxMapY = Math.max(scaleToMapY(top), scaleToMapY(bottom));

        int mapWidth = Math.max(1, maxMapX - minMapX + 1);
        int mapHeight = Math.max(1, maxMapY - minMapY + 1);
        long selectedCells = (long)mapWidth * (long)mapHeight;
        int sourceLength = Math.max(1, renderSourceLength);
        int mapLength = Math.max(1, renderMapLength);
        long estimatedBytes = Math.max(1L, Math.round((selectedCells * (double)sourceLength) / (double)mapLength));

        JLabel label = new JLabel(String.format("Selection: %d x %d cells (%d), ~%d bytes", mapWidth, mapHeight, selectedCells, estimatedBytes));
        JPanel panel = new JPanel();
        if(mainInterface.theme == 1) {
            panel.setBackground(Color.black);
            label.setForeground(Color.white);
        }
        panel.add(label);
        hideSelectionPopup();
        popupSelection = PopupFactory.getSharedInstance().getPopup(this, panel, e.getXOnScreen() + 8, e.getYOnScreen() + 8);
        popupSelection.show();

        Timer popupTimer = new Timer(1800, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                hideSelectionPopup();
            }
        });
        popupTimer.setRepeats(false);
        popupTimer.start();

        resetSelection();
        repaint();
    }

    private static class RenderDataWindow {
        private final byte[] data;
        private final int rangeStart;
        private final int rangeLength;
        private final int dataWindowBaseOffset;

        private RenderDataWindow(byte[] data, int rangeStart, int rangeLength, int dataWindowBaseOffset) {
            this.data = data;
            this.rangeStart = rangeStart;
            this.rangeLength = rangeLength;
            this.dataWindowBaseOffset = dataWindowBaseOffset;
        }
    }

    private static class PointCache {
        private final Scurve mapRef;
        private final int width;
        private final int height;
        private final int length;
        private final int[] pointX;
        private final int[] pointY;

        private PointCache(Scurve mapRef, int width, int height, int length, int[] pointX, int[] pointY) {
            this.mapRef = mapRef;
            this.width = width;
            this.height = height;
            this.length = length;
            this.pointX = pointX;
            this.pointY = pointY;
        }
    }

    private static class SourceOffsetCache {
        private final int mapLength;
        private final int sourceLength;
        private final int[] offsets;

        private SourceOffsetCache(int mapLength, int sourceLength, int[] offsets) {
            this.mapLength = mapLength;
            this.sourceLength = sourceLength;
            this.offsets = offsets;
        }
    }
    
    public byte[] getCurrentData(){
        byte[] sourceData = mainInterface.getData();
        if(sourceData == null || sourceData.length == 0) {
            return new byte[0];
        }
        data = sourceData;

        int lowerBound = Math.max(0, Math.min(dataMicroSlider.getValue(), sourceData.length - 1));
        int upperBound = Math.max(lowerBound + 1, Math.min(dataMicroSlider.getUpperValue(), sourceData.length));
        int length = Math.max(1, upperBound - lowerBound);
        if(reusableCurrentDataBuffer.length != length) {
            reusableCurrentDataBuffer = new byte[length];
        }
        System.arraycopy(sourceData, lowerBound, reusableCurrentDataBuffer, 0, Math.min(length, sourceData.length - lowerBound));
        return reusableCurrentDataBuffer;
    }

    private RenderDataWindow getCurrentRenderDataWindow() {
        byte[] sourceData = mainInterface.getData();
        if(sourceData == null || sourceData.length == 0) {
            return new RenderDataWindow(EMPTY_BYTES, 0, 0, 0);
        }

        int lowerBound = Math.max(0, Math.min(dataMicroSlider.getValue(), sourceData.length - 1));
        int upperBound = Math.max(lowerBound + 1, Math.min(dataMicroSlider.getUpperValue(), sourceData.length));
        int rangeLength = Math.max(1, upperBound - lowerBound);
        int windowOffset = dataRangeSlider != null ? dataRangeSlider.getValue() : 0;
        return new RenderDataWindow(sourceData, lowerBound, rangeLength, Math.max(0, windowOffset));
    }

    private boolean isInteractiveSliderAdjustment() {
        return dataMacroSlider.getValueIsAdjusting()
                || dataMicroSlider.getValueIsAdjusting()
                || (dataRangeSlider != null && dataRangeSlider.getValueIsAdjusting());
    }

    private PointCache getOrBuildPointCache(Scurve activeMap) {
        if(activeMap == null) {
            return null;
        }
        PointCache cache = cachedPointCache;
        int mapLength = activeMap.getLength();
        if(cache != null && cache.mapRef == activeMap && cache.length == mapLength) {
            return cache;
        }

        synchronized(pointCacheLock) {
            cache = cachedPointCache;
            if(cache != null && cache.mapRef == activeMap && cache.length == mapLength) {
                return cache;
            }
            TwoIntegerTuple dimensions = activeMap.dimensions();
            int width = dimensions.get(0);
            int height = dimensions.get(1);
            int[] pointX = new int[mapLength];
            int[] pointY = new int[mapLength];
            for(int i = 0; i < mapLength; i++) {
                TwoIntegerTuple p = (TwoIntegerTuple)activeMap.point(i);
                pointX[i] = p.get(0);
                pointY[i] = p.get(1);
            }
            cache = new PointCache(activeMap, width, height, mapLength, pointX, pointY);
            cachedPointCache = cache;
            return cache;
        }
    }

    private int[] getOrBuildSourceOffsets(int mapLength, int sourceLength) {
        SourceOffsetCache cache = cachedSourceOffsetCache;
        if(cache != null && cache.mapLength == mapLength && cache.sourceLength == sourceLength) {
            return cache.offsets;
        }
        synchronized(sourceOffsetCacheLock) {
            cache = cachedSourceOffsetCache;
            if(cache != null && cache.mapLength == mapLength && cache.sourceLength == sourceLength) {
                return cache.offsets;
            }
            int[] offsets = new int[mapLength];
            int denominator = Math.max(1, mapLength);
            for(int i = 0; i < mapLength; i++) {
                int offset = (int)(((long)i * (long)sourceLength) / (long)denominator);
                offsets[i] = Math.max(0, Math.min(sourceLength - 1, offset));
            }
            cachedSourceOffsetCache = new SourceOffsetCache(mapLength, sourceLength, offsets);
            return offsets;
        }
    }

    private Scurve getRenderMapForCurrentState(Scurve activeMap) {
        return activeMap;
    }

    private boolean updateGuideFocusFromPointer(MouseEvent e, boolean forceUpdate) {
        if(e.getX() < 0 || e.getY() < 0 || e.getX() >= getWidth() || e.getY() >= getHeight()) {
            return false;
        }
        Scurve lookupMap = renderLookupMap != null ? renderLookupMap : map;
        if(lookupMap == null) {
            return false;
        }
        TwoIntegerTuple p = new TwoIntegerTuple(scaleToMapX(e.getX()), scaleToMapY(e.getY()));
        int loc = lookupMap.index(p);
        updateGuideFocus(lookupMap, loc, forceUpdate);
        return true;
    }

    private BufferedImage getGuideOverlayForMap(Scurve activeMap) {
        if(!guidePathEnabled || activeMap == null) {
            return null;
        }

        int length = activeMap.getLength();
        if(length < 2) {
            return null;
        }
        TwoIntegerTuple dimensions = activeMap.dimensions();
        int width = dimensions.get(0);
        int height = dimensions.get(1);

        int focus = guideFocusIndex >= 0 ? guideFocusIndex : (length / 2);
        focus = Math.max(0, Math.min(length - 1, focus));
        int halfTrail = Math.max(16, guideTrailLength / 2);
        int start = Math.max(0, focus - halfTrail);
        int end = Math.min(length - 1, focus + halfTrail);

        String cacheKey = activeMap.type + ":" + width + "x" + height + ":" + start + ":" + end + ":" + focus + ":" + guideTrailLength;
        if(cachedGuideOverlay != null && cacheKey.equals(cachedGuideOverlayKey)) {
            return cachedGuideOverlay;
        }

        BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D guideGraphics = overlay.createGraphics();
        guideGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        guideGraphics.setStroke(new BasicStroke(1.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        TwoIntegerTuple previousPoint = (TwoIntegerTuple)activeMap.point(start);
        for(int idx = start + 1; idx <= end; idx++) {
            TwoIntegerTuple currentPoint = (TwoIntegerTuple)activeMap.point(idx);
            float progress = (idx - start) / (float)Math.max(1, end - start);
            int red = (int)(40 + (215 * progress));
            int green = (int)(220 - (160 * progress));
            int blue = (int)(255 - (140 * progress));
            int alpha = (int)(70 + (130 * progress));
            guideGraphics.setColor(new Color(red, green, blue, alpha));
            guideGraphics.drawLine(previousPoint.get(0), previousPoint.get(1), currentPoint.get(0), currentPoint.get(1));
            previousPoint = currentPoint;
        }

        TwoIntegerTuple startPoint = (TwoIntegerTuple)activeMap.point(start);
        TwoIntegerTuple endPoint = (TwoIntegerTuple)activeMap.point(end);
        TwoIntegerTuple focusPoint = (TwoIntegerTuple)activeMap.point(focus);
        guideGraphics.setColor(new Color(0, 220, 255, 190));
        guideGraphics.fillOval(startPoint.get(0) - 2, startPoint.get(1) - 2, 5, 5);
        guideGraphics.setColor(new Color(255, 64, 64, 210));
        guideGraphics.fillOval(endPoint.get(0) - 2, endPoint.get(1) - 2, 5, 5);
        guideGraphics.setColor(new Color(255, 210, 0, 230));
        guideGraphics.fillOval(focusPoint.get(0) - 3, focusPoint.get(1) - 3, 7, 7);
        guideGraphics.dispose();

        cachedGuideOverlay = overlay;
        cachedGuideOverlayKey = cacheKey;
        return overlay;
    }

    private void setGuideTrailLength(int nextLength) {
        guideTrailLength = Math.max(64, nextLength);
        cachedGuideOverlayKey = null;
        if(guidePathEnabled) {
            draw();
        }
    }

    private void updateGuideFocus(Scurve lookupMap, int loc, boolean forceUpdate) {
        if(!guidePathEnabled || lookupMap == null) {
            return;
        }
        int length = lookupMap.getLength();
        if(length <= 0) {
            return;
        }
        int clampedLoc = Math.max(0, Math.min(length - 1, loc));
        if(!forceUpdate) {
            if(clampedLoc == lastGuideFocusRenderedIndex) {
                return;
            }
            long now = System.currentTimeMillis();
            if(now - lastGuideFocusUpdateMs < GUIDE_FOCUS_UPDATE_THROTTLE_MS) {
                return;
            }
            lastGuideFocusUpdateMs = now;
        } else {
            lastGuideFocusUpdateMs = System.currentTimeMillis();
        }

        guideFocusIndex = clampedLoc;
        lastGuideFocusRenderedIndex = clampedLoc;
        refreshGuideOverlayOnly();
    }

    private void refreshGuideOverlayOnly() {
        if(!guidePathEnabled) {
            return;
        }
        Scurve activeMap = renderLookupMap != null ? renderLookupMap : map;
        if(activeMap == null) {
            return;
        }
        BufferedImage overlay = getGuideOverlayForMap(activeMap);
        if(SwingUtilities.isEventDispatchThread()) {
            renderedGuideOverlay = overlay;
            repaint();
        } else {
            SwingUtilities.invokeLater(() -> {
                renderedGuideOverlay = overlay;
                repaint();
            });
        }
    }

    public void createPopupMenu(JFrame frame){
        popupMenu = new JPopupMenu("Menu");
        JMenuItem pause = new JMenuItem("Pause");

        pause.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                cantordust.cdprint("clicked pause\n");
            }
        });
        
        JMenuItem hilbert = new JMenuItem("Hilbert");
        hilbert.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(!csource.isType("classifierPrediction")) {
                    isClassifier = false;
                }
                if(!map.isType("hilbert")) {
                    map = new Hilbert(cantordust, 2, (int)(Math.log(getWindowSize())/Math.log(2)));
                    draw();
                    cantordust.cdprint("clicked hilbert\n");
                } else { cantordust.cdprint("clicked hilbert\nAlready set\n"); }
            }
        });

        JMenuItem linear = new JMenuItem("Linear");
        linear.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!map.isType("linear")) {
                    cantordust.cdprint("clicked linear\n");
                    double x = Math.pow(getWindowSize(),2);
                    map = new Linear(cantordust, 2, x);
                    draw();
                } else { cantordust.cdprint("clicked linear\nAlready set\n"); }
            }
        });

        JMenuItem zorder = new JMenuItem("Zorder");
        zorder.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!map.isType("zorder")) {
                    cantordust.cdprint("clicked zorder\n");
                    double x = Math.pow(getWindowSize(),2);
                    map = new Zorder(cantordust, 2, x);
                    draw();
                } else { cantordust.cdprint("clicked zorder\nAlready set\n"); }
            }
        });

        JMenuItem hcurve = new JMenuItem("HCurve");
        hcurve.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!map.isType("hcurve")) {
                    cantordust.cdprint("clicked hcurve\n");
                    double x = Math.pow(getWindowSize(),2);
                    map = new HCurve(cantordust, 2, x);
                    draw();
                } else { cantordust.cdprint("clicked hcurve\nAlready set\n"); }
            }
        });

        JMenuItem _8bpp = new JMenuItem("8bpp");
        _8bpp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("8bpp")) {
                    csource = new Color8bpp(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked 8bpp\n");
                } else { cantordust.cdprint("clicked 8bpp\nAlready set\n"); }
            }
        });

        JMenuItem _16bpp = new JMenuItem("16bpp ARGB1555");
        _16bpp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("16bpp ARGB1555")) {
                    csource = new Color16bpp_ARGB1555(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked 16bpp\n");
                } else { cantordust.cdprint("clicked 16bpp\nAlready set\n"); }
            }
        });

        JMenuItem _24bpp = new JMenuItem("24bpp Rgb");
        _24bpp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("24bpp")) {
                    csource = new Color24bpp(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked 24bpp\n");
                } else { cantordust.cdprint("clicked 24bpp\nAlready set\n"); }
            }
        });

        JMenuItem _32bpp = new JMenuItem("32bpp Rgb");
        _32bpp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("32bpp")) {
                    csource = new Color32bpp(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked 32bpp\n");
                } else { cantordust.cdprint("clicked 32bpp\nAlready set\n"); }
            }
        });
        
        JMenuItem _64bpp = new JMenuItem("64bpp Rgb");
        _64bpp.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("64bpp")) {
                    csource = new Color64bpp(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked 64bpp\n");
                } else { cantordust.cdprint("clicked 64bpp\nAlready set\n"); }
            }
        });

        JMenuItem entropy = new JMenuItem("Entropy");
        entropy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("entropy")) {
                    csource = new ColorEntropy(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked entropy\n");
                } else { cantordust.cdprint("clicked entropy\nAlready set\n"); }
            }
        });

        JMenuItem byteClass = new JMenuItem("Byte Class");
        byteClass.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("class")) {
                    csource = new ColorClass(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked byte class\n");
                } else { cantordust.cdprint("clicked byte class\nAlready set\n"); }
            }
        });

        JMenuItem gradient = new JMenuItem("Gradient");
        gradient.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("gradient")) {
                    csource = new ColorGradient(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked gradient\n");
                } else { cantordust.cdprint("clicked gradient\nAlready set\n"); }
            }
        });

        JMenuItem spectrum = new JMenuItem("Spectrum");
        spectrum.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {   
                if(!csource.isType("spectrum")) {
                    csource = new ColorSpectrum(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked spectrum\n");
                } else { cantordust.cdprint("clicked spectrum\nAlready set\n"); }
            }
        });

        JMenuItem prediction = new JMenuItem("Classifier prediction");
        prediction.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(!csource.isType("classifierPrediction")) {
                    isClassifier = true;
                    csource = new ColorClassifierPrediction(cantordust, getCurrentData());
                    draw();
                    cantordust.cdprint("clicked classifier prediction\n");
                } else { cantordust.cdprint("clicked classifier prediction\nAlready set\n"); }
            }
        });
        
        /*JMenuItem stopClassifier = new JMenuItem("Stop Classifier");
        stopClassifier.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                isClassifier = false;
                popupMenu.remove(stopClassifier);
                cantordust.cdprint("Classifier Stopped\n");
            }
        });*/

        JMenuItem close = new JMenuItem("Close");
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                cantordust.cdprint("clicked close\n");
            }
        });

        JMenuItem classGen = new JMenuItem("Generate Classifier");
        classGen.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                // draw();
                cantordust.cdprint("clicked Generate Classifier\n");
                //isClassifier = true;
                cantordust.initiateClassifier();
                popupMenu.remove(close);
                //popupMenu.add(stopClassifier);
                popupMenu.add(close);
                cantordust.cdprint("generated classifier\n");
            }
        });

        JCheckBoxMenuItem guidePath = new JCheckBoxMenuItem("Sequence Guide");
        guidePath.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                guidePathEnabled = guidePath.isSelected();
                cachedGuideOverlayKey = null;
                lastGuideFocusRenderedIndex = -1;
                lastGuideFocusUpdateMs = 0L;
                if(guidePathEnabled) {
                    draw();
                } else {
                    renderedGuideOverlay = null;
                    repaint();
                }
            }
        });

        JCheckBoxMenuItem interpolationToggle = new JCheckBoxMenuItem("Interpolation", RenderSettings.isInterpolationEnabled());
        interpolationToggle.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                RenderSettings.setInterpolationEnabled(interpolationToggle.isSelected());
                repaint();
            }
        });

        JMenu guideTrailMenu = new JMenu("Guide Trail Length");
        ButtonGroup guideTrailGroup = new ButtonGroup();
        JRadioButtonMenuItem guideTrailMedium = new JRadioButtonMenuItem("Medium (1536)", true);
        JRadioButtonMenuItem guideTrailLong = new JRadioButtonMenuItem("Long (6144)");

        guideTrailMedium.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setGuideTrailLength(1536);
            }
        });
        guideTrailLong.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setGuideTrailLength(6144);
            }
        });

        guideTrailGroup.add(guideTrailMedium);
        guideTrailGroup.add(guideTrailLong);
        guideTrailMenu.add(guideTrailMedium);
        guideTrailMenu.add(guideTrailLong);

        JMenu locality = new JMenu("Locality");
        locality.add(hilbert);
        locality.add(linear);
        locality.add(zorder);
        locality.add(hcurve);
        
        JMenu byteColor = new JMenu("Byte");
        byteColor.add(_8bpp);
        byteColor.add(_16bpp);
        byteColor.add(_24bpp);
        byteColor.add(_32bpp);
        byteColor.add(_64bpp);
        
        JMenu shading = new JMenu("Shading");
        shading.add(byteColor);
        shading.add(byteClass);
        shading.add(entropy);
        shading.add(gradient);
        shading.add(spectrum);
        shading.add(prediction);
        
        popupMenu.add(pause);
        popupMenu.add(locality);
        popupMenu.add(shading);
        popupMenu.add(interpolationToggle);
        popupMenu.add(guidePath);
        popupMenu.add(guideTrailMenu);
        popupMenu.add(classGen);
        popupMenu.add(close);
        this.add(popupMenu);
    }

    private void draw() {
        if(!isShowing() && mainInterface.currVis != this && renderedImage != null) {
            return;
        }
        if(!csource.isType("classifierPrediction")) {
            isClassifier = false;
        }
        synchronized(drawMonitor) {
            redrawRequested = true;
            drawRequestVersion++;
            if(drawInProgress) {
                return;
            }
            drawInProgress = true;
        }

        drawExecutor.execute(() -> {
            while(true) {
                long requestVersion;
                synchronized(drawMonitor) {
                    if(!redrawRequested) {
                        drawInProgress = false;
                        break;
                    }
                    redrawRequested = false;
                    requestVersion = drawRequestVersion;
                }
                renderCurrentState(requestVersion);
            }
        });
    }

    public void requestRender() {
        draw();
    }

    private void renderCurrentState(long requestVersion) {
        RenderDataWindow renderDataWindow = getCurrentRenderDataWindow();
        if(renderDataWindow.rangeLength <= 0 || renderDataWindow.data.length == 0) {
            return;
        }
        int currentDataBaseOffset = renderDataWindow.dataWindowBaseOffset + renderDataWindow.rangeStart;

        ColorSource activeSource = this.csource;
        Scurve activeMap = this.map;
        String activePlotType = this.type_plot;
        boolean interactiveSliderAdjustment = isInteractiveSliderAdjustment();
        Scurve renderMap = getRenderMapForCurrentState(activeMap);
        if(renderMap == null) {
            return;
        }

        activeSource.setData(renderDataWindow.data);
        if(activeSource instanceof ColorClassifierPrediction) {
            ((ColorClassifierPrediction) activeSource).setBaseOffset(renderDataWindow.dataWindowBaseOffset);
        }

        if(activePlotType.equals("unrolled")) {
            drawMap_unrolled(renderMap.type, size_hilbert, activeSource/*, dst, prog*/);
        } else if(activePlotType.equals("square")) {
            drawMap_square(renderMap, activeSource, currentDataBaseOffset, requestVersion, interactiveSliderAdjustment, renderDataWindow.rangeStart, renderDataWindow.rangeLength/*, dst, prog*/);
        }
    }

    public void drawMap_square(Scurve activeMap, ColorSource activeSource, int dataBaseOffset, long requestVersion, boolean interactiveSliderAdjustment, int sourceRangeStart, int sourceRangeLength/*, String name, prog */) {
        // prog.set_target(Math.pow(size, 2))
        // if(this.map.isType("hilbert")){
        //     cantordust.cdprint("")
        //     this.map = new Hilbert(this.cantordust, 2, (int)(Math.log(getWindowSize())/Math.log(2)));
        // } else if (this.map.isType("zigzag")){
        //     this.map = new ZigZag(this.cantordust, 2, (double)getWindowSize());
        // }
        PointCache pointCache = getOrBuildPointCache(activeMap);
        if(pointCache == null) {
            return;
        }
        int width = pointCache.width;
        int height = pointCache.height;
        int mapLength = pointCache.length;
        int[] nextPixelMap1D = new int[height * width];
        boolean playbackActive = mainInterface.isPlaybackActive();
        BufferedImage guideOverlay = (interactiveSliderAdjustment || playbackActive) ? null : getGuideOverlayForMap(activeMap);

        int sourceDataLength = Math.max(1, activeSource.getLength());
        int sourceRangeLow = Math.max(0, Math.min(sourceRangeStart, sourceDataLength - 1));
        int sourceLength = Math.max(1, Math.min(sourceRangeLength, sourceDataLength - sourceRangeLow));
        int[] sourceOffsets = getOrBuildSourceOffsets(mapLength, sourceLength);
        int lastSourceIndex = -1;
        int cachedColorArgb = 0xFF000000;
        boolean allowEarlyCancel = interactiveSliderAdjustment
                || (playbackActive && (System.currentTimeMillis() - lastRenderPublishMs) < 20L);
        int cancelMask = 63;
        for(int i=0; i<mapLength; i++){
            if(allowEarlyCancel && (i & cancelMask) == 0 && requestVersion != drawRequestVersion) {
                return;
            }
            int sourceIndex = sourceRangeLow + sourceOffsets[i];
            if(sourceIndex != lastSourceIndex) {
                cachedColorArgb = activeSource.pointArgb(sourceIndex);
                lastSourceIndex = sourceIndex;
            }
            int pixelIndex = pointCache.pointY[i] * width + pointCache.pointX[i];
            if(pixelIndex >= 0 && pixelIndex < nextPixelMap1D.length) {
                nextPixelMap1D[pixelIndex] = cachedColorArgb;
            }
        }

        if(allowEarlyCancel && requestVersion != drawRequestVersion) {
            return;
        }

        //c.save(name);
        plotMap(new TwoIntegerTuple(width, height), nextPixelMap1D, dataBaseOffset, activeMap, guideOverlay, sourceLength, mapLength);
    }

    public void drawMap_unrolled(String map_type, int size, ColorSource csource/*, String name, prog */) {
        cantordust.cdprint("draw unrolled map in-progress");
    }
    
    public void create2DPixelMap(TwoIntegerTuple dimensions){
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        this.pixelMap2D = new int[height][width*3];
    }
    
    public void add2DPixel(TwoIntegerTuple point, Rgb color){
        /*
        adds 2D pixel to pixelMap2D
        */
        int x = point.get(0)*3;
        int y = point.get(1);
        this.pixelMap2D[y][x] = color.r;
        this.pixelMap2D[y][x+1] = color.g;
        this.pixelMap2D[y][x+2] = color.b;
    }

    private void add2DPixel(int[][] targetPixelMap, TwoIntegerTuple point, Rgb color) {
        int x = point.get(0)*3;
        int y = point.get(1);
        targetPixelMap[y][x] = color.r;
        targetPixelMap[y][x+1] = color.g;
        targetPixelMap[y][x+2] = color.b;
    }

    public void flip2DPixlMap(TwoIntegerTuple dimensions){
        /*
        flips every other row in pixels to fit raster image
        */
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        for(int row = 0; row < height; row++){
            if(!(row%2==0)){
                int[] temp = this.pixelMap2D[row];
                int j = width*3-4;
                for(int i = 0; i < width*3; i+=3){
                    this.pixelMap2D[row][i] = temp[j];
                    this.pixelMap2D[row][i+1] = temp[j+1];
                    this.pixelMap2D[row][i+2] = temp[j+2];
                    j-=3;
                }
            }
        }
    }
    
    public void convertPixelMapTo1D(){
        /*
        convert 2D Pixel Map to 1D Pixel Map (pixels)
        */
        this.pixelMap1D = new int[this.pixelMap2D.length*this.pixelMap2D[0].length];
        int x = 0;
        for(int i=0; i<this.pixelMap2D.length; i++) {
            for(int j=0; j<pixelMap2D[i].length; j++) {
                this.pixelMap1D[x] = this.pixelMap2D[i][j];
                x++;
            }
        }
    }

    private int[] convertPixelMapTo1D(int[][] sourcePixelMap) {
        int[] oneDimensionalPixels = new int[sourcePixelMap.length * sourcePixelMap[0].length];
        int index = 0;
        for(int i=0; i<sourcePixelMap.length; i++) {
            for(int j=0; j<sourcePixelMap[i].length; j++) {
                oneDimensionalPixels[index] = sourcePixelMap[i][j];
                index++;
            }
        }
        return oneDimensionalPixels;
    }
    
    public void plotMap(TwoIntegerTuple dimensions){
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        // int imageSize = width * height * 3;
        // JPanel panel = new JPanel();
        // getContentPane().removeAll();
        // getContentPane().add(panel);
        // panel.add( createImageLabel(this.pixelMap1D, width, height) );
        // panel.revalidate();
        // panel.repaint();
        plotMap(dimensions, this.pixelMap1D, this.memLocBaseOffset, this.renderLookupMap, this.renderedGuideOverlay, this.renderSourceLength, this.renderMapLength);
    }

    private void plotMap(TwoIntegerTuple dimensions, int[] pixels, int nextBaseOffset, Scurve nextLookupMap, BufferedImage nextGuideOverlay, int nextSourceLength, int nextMapLength){
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] targetPixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, targetPixels, 0, Math.min(targetPixels.length, pixels.length));

        SwingUtilities.invokeLater(() -> {
            this.memLocBaseOffset = nextBaseOffset;
            this.renderSourceLength = Math.max(1, nextSourceLength);
            this.renderMapLength = Math.max(1, nextMapLength);
            this.lastRenderPublishMs = System.currentTimeMillis();
            this.renderLookupMap = nextLookupMap;
            this.renderedImage = image;
            this.renderedGuideOverlay = nextGuideOverlay;
            repaint();
        });
    }
    
    private JLabel createImageLabel(int[] pixels, int width, int height)
    {
        // int change = size_hilbert - (int)((width - size_hilbert)/2);
        // cantordust.cdprint("ch: "+change+"\n");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] targetPixels = ((DataBufferInt)image.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, targetPixels, 0, Math.min(targetPixels.length, pixels.length));
        return new JLabel( new ImageIcon(image) );
    }

    public static int getWindowSize() {
        return size_hilbert;
    }
}