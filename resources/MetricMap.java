package resources;

// metricMap
import javax.swing.*;
import javax.swing.JPanel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.*;
import java.util.HashMap;
import java.awt.image.*;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class MetricMap extends Visualizer{
    protected byte[] data;
    protected static int size_hilbert = 512;
    private static final int PLAYBACK_RENDER_SIZE = 256;
    private static final long GUIDE_FOCUS_UPDATE_THROTTLE_MS = 16L;
    protected int[][] pixelMap2D;
    protected int[] pixelMap1D;
    protected HashMap<Integer, Integer> memLoc = new HashMap<Integer, Integer>();
    private volatile int memLocBaseOffset = 0;
    private volatile Scurve renderLookupMap;
    private volatile BufferedImage renderedImage;
    private volatile BufferedImage renderedGuideOverlay;
    private Scurve playbackHilbertMap;
    private Scurve playbackZorderMap;
    private Scurve playbackHcurveMap;
    private Linear playbackLinearMap;
    private BufferedImage cachedGuideOverlay;
    private String cachedGuideOverlayKey;
    private boolean guidePathEnabled = false;
    private int guideTrailLength = 1536;
    private int guideFocusIndex = -1;
    private int lastGuideFocusRenderedIndex = -1;
    private long lastGuideFocusUpdateMs = 0L;
    protected Popup popupAddr;
    protected JPopupMenu popupMenu;
    protected Scurve map;
    protected JPanel panel = new JPanel();
    protected String type_plot = "square";
    protected ColorSource csource;
    private JSlider dataWidthSlider;
    private final Object drawMonitor = new Object();
    private boolean drawInProgress = false;
    private boolean redrawRequested = false;
    private boolean isClassifier = false;

    public MetricMap(int windowSize, GhidraSrc cantordust, JFrame frame, Boolean isCurrentView) {
        super(windowSize, cantordust);
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

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if(renderedImage != null) {
            g.drawImage(renderedImage, 0, 0, getWidth(), getHeight(), null);
        }
        if(guidePathEnabled && renderedGuideOverlay != null) {
            g.drawImage(renderedGuideOverlay, 0, 0, getWidth(), getHeight(), null);
        }
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
                    if(guidePathEnabled) {
                        updateGuideFocusFromPointer(e, false);
                    }
                    if(popupAddr != null) {
                        popupAddr.hide();
                    }
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
                    HashMap<Integer, Integer> memLocSnapshot = memLoc;
                    Integer relativeLocation = memLocSnapshot.get(loc);
                    if(relativeLocation == null) {
                        return;
                    }
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
                if(popupAddr != null) {
                    popupAddr.hide();
                }
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
    
    public byte[] getCurrentData(){
        int lowerBound = dataMicroSlider.getValue();
        int upperBound = dataMicroSlider.getUpperValue();
        byte[] currentData = new byte[upperBound-lowerBound];
        // this.cantordust.cdprint(String.format("%d %d\n", lowerBound, upperBound));
        for(int i=lowerBound; i <= upperBound-2; i++) {
            currentData[i-lowerBound] = data[i];
        }
        return currentData;
    }

    private int getCurrentDataBaseOffset() {
        int baseOffset = dataMicroSlider.getValue();
        if(dataRangeSlider != null) {
            baseOffset += dataRangeSlider.getValue();
        }
        return Math.max(0, baseOffset);
    }

    private Scurve getRenderMapForCurrentState(Scurve activeMap) {
        if(activeMap == null) {
            return null;
        }
        if(!mainInterface.isPlaybackActive()) {
            return activeMap;
        }

        double playbackSize = Math.pow(PLAYBACK_RENDER_SIZE, 2);
        try {
            if(activeMap.isType("hilbert")) {
                if(playbackHilbertMap == null) {
                    playbackHilbertMap = new Hilbert(cantordust, 2, playbackSize);
                }
                return playbackHilbertMap;
            }
            if(activeMap.isType("zorder")) {
                if(playbackZorderMap == null) {
                    playbackZorderMap = new Zorder(cantordust, 2, playbackSize);
                }
                return playbackZorderMap;
            }
            if(activeMap.isType("hcurve")) {
                if(playbackHcurveMap == null) {
                    playbackHcurveMap = new HCurve(cantordust, 2, playbackSize);
                }
                return playbackHcurveMap;
            }
            if(activeMap.isType("linear")) {
                if(playbackLinearMap == null) {
                    playbackLinearMap = new Linear(cantordust, 2, playbackSize);
                }
                int sourceWidth = Math.max(1, activeMap.getWidth());
                int sourceHeight = Math.max(1, activeMap.getHeight());
                int reference = Math.max(sourceWidth, sourceHeight);
                double scale = (double)PLAYBACK_RENDER_SIZE / (double)Math.max(1, reference);
                playbackLinearMap.setWidth(Math.max(1, (int)Math.round(sourceWidth * scale)));
                playbackLinearMap.setHeight(Math.max(1, (int)Math.round(sourceHeight * scale)));
                return playbackLinearMap;
            }
        } catch(RuntimeException ignored) {
            return activeMap;
        }

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
                if(!csource.type.equals("classifierPrediction")) {
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
        if(!csource.type.equals("classifierPrediction")) {
            isClassifier = false;
        }
        synchronized(drawMonitor) {
            redrawRequested = true;
            if(drawInProgress) {
                return;
            }
            drawInProgress = true;
        }

        Thread renderThread = new Thread(() -> {
            while(true) {
                synchronized(drawMonitor) {
                    if(!redrawRequested) {
                        drawInProgress = false;
                        break;
                    }
                    redrawRequested = false;
                }
                renderCurrentState();
            }
        }, "cantordust-metric-map-render");
        renderThread.setDaemon(true);
        renderThread.start();
    }

    private void renderCurrentState() {
        byte[] currentData = getCurrentData();
        if(currentData.length == 0) {
            return;
        }
        int currentDataBaseOffset = getCurrentDataBaseOffset();

        ColorSource activeSource = this.csource;
        Scurve activeMap = this.map;
        String activePlotType = this.type_plot;
        Scurve renderMap = getRenderMapForCurrentState(activeMap);
        if(renderMap == null) {
            return;
        }

        activeSource.setData(currentData);
        if(activeSource instanceof ColorClassifierPrediction) {
            ((ColorClassifierPrediction) activeSource).setBaseOffset(currentDataBaseOffset);
        }
        if(activeSource.isType("spectrum")) {
            activeSource = new ColorSpectrum(cantordust, currentData);
        }

        if(activePlotType.equals("unrolled")) {
            this.cantordust.cdprint("building unrolled "+renderMap.type+" curve\n");
            drawMap_unrolled(renderMap.type, size_hilbert, activeSource/*, dst, prog*/);
        } else if(activePlotType.equals("square")) {
            this.cantordust.cdprint("Building square "+renderMap.type+" curve\n");
            drawMap_square(renderMap, activeSource, currentDataBaseOffset/*, dst, prog*/);
        }
    }

    public void drawMap_square(Scurve activeMap, ColorSource activeSource, int dataBaseOffset/*, String name, prog */) {
        // prog.set_target(Math.pow(size, 2))
        // if(this.map.isType("hilbert")){
        //     cantordust.cdprint("")
        //     this.map = new Hilbert(this.cantordust, 2, (int)(Math.log(getWindowSize())/Math.log(2)));
        // } else if (this.map.isType("zigzag")){
        //     this.map = new ZigZag(this.cantordust, 2, (double)getWindowSize());
        // }
        TwoIntegerTuple dimensions = activeMap.dimensions();
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        int[][] nextPixelMap2D = new int[height][width*3];
        HashMap<Integer, Integer> nextMemLoc = new HashMap<Integer, Integer>();
        BufferedImage guideOverlay = getGuideOverlayForMap(activeMap);

        float step = (float)activeSource.getLength()/(float)(activeMap.getLength());
        for(int i=0; i<activeMap.getLength(); i++){
            TwoIntegerTuple p = (TwoIntegerTuple)activeMap.point(i);
            Rgb c = activeSource.point((int)(i*step));
            add2DPixel(nextPixelMap2D, p, c);
            nextMemLoc.put(i, (int)(i*step));
        }

        int[] nextPixelMap1D = convertPixelMapTo1D(nextPixelMap2D);
        //c.save(name);
        plotMap(dimensions, nextPixelMap1D, nextMemLoc, dataBaseOffset, activeMap, guideOverlay);
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

    public void addMemLoc(int idx, int rloc) {
        /*
        adds Memory relative memory location to XY coordinate
        */
        this.memLoc.put(idx, rloc);
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
        plotMap(dimensions, this.pixelMap1D, this.memLoc, this.memLocBaseOffset, this.renderLookupMap, this.renderedGuideOverlay);
    }

    private void plotMap(TwoIntegerTuple dimensions, int[] pixels, HashMap<Integer, Integer> nextMemLoc, int nextBaseOffset, Scurve nextLookupMap, BufferedImage nextGuideOverlay){
        int width = dimensions.get(0);
        int height = dimensions.get(1);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        WritableRaster raster = image.getRaster();
        raster.setPixels(0, 0, width, height, pixels);

        SwingUtilities.invokeLater(() -> {
            this.memLocBaseOffset = nextBaseOffset;
            this.memLoc = nextMemLoc;
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
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // cantordust.cdprint("w: "+width+"\nh: "+height+"\n");
        WritableRaster raster = image.getRaster();
        raster.setPixels(0, 0, width, height, pixels);
        return new JLabel( new ImageIcon(image) );
    }

    public static int getWindowSize() {
        return size_hilbert;
    }
}