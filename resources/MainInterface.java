package resources;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainInterface extends JPanel {
    private static final int DATA_WINDOW_UPDATE_THROTTLE_MS = 16;
    private static final int DATA_WINDOW_BUFFER_POOL_SIZE = 4;
    private byte[] data;
    private byte[] fullData;
    private static final int DATA_WINDOW_SIZE = 1048575;
    public BitMapSlider macroSlider;
    public BitMapSlider microSlider;
    public JSlider widthSlider;
    public JSlider offsetSlider;
    public JSlider dataSlider;
    public JButton widthDownButton;
    public JButton widthUpButton;
    public JButton offsetDownButton;
    public JButton offsetUpButton;
    public JButton microUpButton;
    public JButton hilbertMapButton;
    public JButton themeButton;
    public JButton twoTupleButton;
    public JButton eightBitPerPixelBitMapButton;
    public JButton byteCloudButton;
    public JButton metricMapButton;
    public JButton oneTupleButton;
    public JButton playbackPlayButton;
    public JButton playbackPauseButton;
    public JButton playbackStopButton;
    public JPopupMenu popup;

    public GhidraSrc cantordust;
    public JLabel dataRange = new JLabel();
    public JLabel macroValueHigh = new JLabel();
    public JLabel macroValueLow = new JLabel();
    public JLabel microValueHigh = new JLabel();
    public JLabel microValueLow = new JLabel();
    public JLabel widthValue = new JLabel();
    public JLabel offsetValue = new JLabel();
    public JLabel coverageValue = new JLabel();
    public JLabel programName = new JLabel();
    public JLabel playbackTargetLabel = new JLabel();
    public JLabel playbackStepLabel = new JLabel();
    public JLabel playbackIntervalLabel = new JLabel();
    public JCheckBox playbackLoopCheckBox;
    public JComboBox<String> playbackTargetCombo;
    public JSpinner playbackStepSpinner;
    public JSpinner playbackIntervalSpinner;
    public JPanel playbackPanel;

    public JPanel currVis = new JPanel();

    /* visualizers stored here so no duplicate visualizer instances are ever created.*/
    public HashMap<visualizerMapKeys, JPanel> visualizerPanels;

    public enum visualizerMapKeys {
        BITMAP,
        BYTECLOUD,
        METRIC,
        TWOTUPLE,
        ONETUPLE
    }

    private JFrame frame;
    public String basePath;
    public int xOffset = 0;
    protected byte theme;
    protected Boolean dispMetricMap;
    private DataPlaybackController dataPlaybackController;
    private final Object dataWindowUpdateLock = new Object();
    private final ExecutorService dataWindowExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "cantordust-data-window");
        worker.setDaemon(true);
        return worker;
    });
    private boolean dataWindowUpdateInProgress = false;
    private int pendingDataWindowStart = 0;
    private long pendingDataWindowRequestId = 0L;
    private boolean suppressDataSliderCallback = false;
    private final byte[][] dataWindowBufferPool = new byte[DATA_WINDOW_BUFFER_POOL_SIZE][];
    private int dataWindowBufferCursor = 0;
    private int microPreferredExtent = -1;
    private boolean suppressMicroPreferredTracking = false;

    public MainInterface(byte[] mdata, GhidraSrc cd, JFrame frame) throws IOException {
        this.data = mdata;
        this.fullData = mdata;
        this.cantordust = cd;
        this.frame = frame;
        visualizerPanels = new HashMap<>();

        this.dispMetricMap = false;
        this.basePath = this.cantordust.getCurrentDirectory();

        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setLayout(new GridBagLayout());
        setPreferredSize(new Dimension(getWindowWidth(), getWindowHeight()));
        setMinimumSize(new Dimension(900, 620));
        GridBagConstraints gbc = new GridBagConstraints();

        if(fullData.length > 26214400){
            // 0xfffff = 1048575, 25MB = 0x1900000 = 26214400 bytes
            this.data = Arrays.copyOfRange(fullData, 0, DATA_WINDOW_SIZE);
            int range = fullData.length - DATA_WINDOW_SIZE;
            dataSlider = new JSlider(0, range);
            dataSlider.setOrientation(SwingConstants.VERTICAL);
            dataSlider.setInverted(true);
            dataSlider.setValue(0);
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.gridheight = 512;
            xOffset = 5;
            gbc.gridwidth = xOffset;
            gbc.fill = GridBagConstraints.BOTH;
            gbc.anchor = GridBagConstraints.CENTER;
            gbc.insets = new Insets(5, 5, 5, 5);
            add(dataSlider, gbc);
        }
        cantordust.cdprint("data: "+data.length+"\n");
        macroSlider = new BitMapSlider(1, this.data.length-1, this.data, this.cantordust);
        macroSlider.setValue(1);
        macroSlider.setUpperValue(this.data.length-1);
        gbc.gridx = xOffset + 0;
        gbc.gridy = 0;
        gbc.gridheight = 512;
        gbc.gridwidth = 10;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 5, 5, 5);
        add(macroSlider, gbc);
        
        microSlider = new BitMapSlider(0, this.data.length-1, this.data, this.cantordust);
        microSlider.setValue(macroSlider.getValue());
        microSlider.setUpperValue(macroSlider.getUpperValue());
        microPreferredExtent = Math.max(1, microSlider.getExtent());
        gbc.gridx = xOffset + 10;
        add(microSlider, gbc);

        Dimension incDim = new Dimension(18, 18);
        Insets zeroIn = new Insets(0, 0, 0, 0);

        microUpButton = new JButton(">");
        microUpButton.addActionListener(new inc_micro());
        microUpButton.setPreferredSize(incDim);
        microUpButton.setMargin(zeroIn);
        microUpButton.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridx = xOffset + 19;
        gbc.gridy = 512;
        gbc.gridheight = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        add(microUpButton, gbc);

        widthDownButton = new JButton("<");
        widthDownButton.addActionListener(new dec_width());
        widthDownButton.setPreferredSize(incDim);
        widthDownButton.setMargin(zeroIn);
        widthDownButton.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridx = xOffset + 20;
        add(widthDownButton, gbc);

        Dimension slideDim = new Dimension(200, 15);

        widthSlider = new JSlider(1, 1024);
        widthSlider.setValue(512);
        widthSlider.setMaximum(1024);
        widthSlider.setOrientation(SwingConstants.HORIZONTAL);
        widthSlider.setPreferredSize(slideDim);
        widthSlider.setMinimumSize(slideDim);
        widthSlider.setMaximumSize(slideDim);
        gbc.gridy = 512;
        gbc.gridx = xOffset + 21;
        gbc.gridheight = 1;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        add(widthSlider, gbc);

        widthUpButton = new JButton(">");
        widthUpButton.addActionListener(new inc_width());
        widthUpButton.setPreferredSize(incDim);
        widthUpButton.setMargin(zeroIn);
        widthUpButton.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridx = xOffset + 260;
        add(widthUpButton, gbc);

        offsetDownButton = new JButton("<");
        offsetDownButton.addActionListener(new dec_offset());
        offsetDownButton.setPreferredSize(incDim);
        offsetDownButton.setMargin(zeroIn);
        offsetDownButton.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridx = xOffset + 261;
        add(offsetDownButton, gbc);

        offsetSlider = new JSlider(1, 255);
        offsetSlider.setValue(0);
        offsetSlider.setMaximum(255);
        offsetSlider.setOrientation(SwingConstants.HORIZONTAL);
        offsetSlider.setPreferredSize(slideDim);
        offsetSlider.setMinimumSize(slideDim);
        offsetSlider.setMaximumSize(slideDim);
        gbc.gridx = xOffset + 270;
        add(offsetSlider, gbc);

        offsetUpButton = new JButton(">");
        offsetUpButton.addActionListener(new inc_offset());
        offsetUpButton.setPreferredSize(incDim);
        offsetUpButton.setMargin(zeroIn);
        offsetUpButton.setBorder(BorderFactory.createEmptyBorder());
        gbc.gridx = xOffset + 512;
        add(offsetUpButton, gbc);

        addPlaybackControls(gbc);
        dataPlaybackController = new DataPlaybackController(this);
        playbackPlayButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataPlaybackController.play();
            }
        });
        playbackPauseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataPlaybackController.pause();
            }
        });
        playbackStopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataPlaybackController.stop();
            }
        });
        playbackLoopCheckBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataPlaybackController.setLoopEnabled(playbackLoopCheckBox.isSelected());
            }
        });
        playbackTargetCombo.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dataPlaybackController.setTarget(DataPlaybackController.PlaybackTarget.fromLabel((String) playbackTargetCombo.getSelectedItem()));
            }
        });
        playbackStepSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Number value = (Number) playbackStepSpinner.getValue();
                dataPlaybackController.setStepUnits(value.intValue());
            }
        });
        playbackIntervalSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
                Number value = (Number) playbackIntervalSpinner.getValue();
                dataPlaybackController.setIntervalMs(value.intValue());
            }
        });
        dataPlaybackController.setLoopEnabled(playbackLoopCheckBox.isSelected());
        dataPlaybackController.setTarget(DataPlaybackController.PlaybackTarget.fromLabel((String) playbackTargetCombo.getSelectedItem()));
        dataPlaybackController.setStepUnits(((Number) playbackStepSpinner.getValue()).intValue());
        dataPlaybackController.setIntervalMs(((Number) playbackIntervalSpinner.getValue()).intValue());
        
        // Default Current Visualization: MetricMap
        currVis = new MetricMap(MetricMap.getWindowSize(), cantordust, this, frame, true);
        currVis.setPreferredSize(new Dimension(512, 512));
        add(currVis, buildVisualizerConstraints());

        // Setup buttons and button icons
        
        Image twoTupleIcon = ImageIO.read(new File(basePath + "resources/icons/icon_2_tuple.bmp")).getScaledInstance(41, 41, Image.SCALE_SMOOTH);
        twoTupleButton = new JButton(new ImageIcon(twoTupleIcon));
        twoTupleButton.addActionListener(new open_two_tuple());
        twoTupleButton.setPreferredSize(new Dimension(50, 50));
        twoTupleButton.setBackground(Color.darkGray);
        twoTupleButton.setToolTipText("Two Tuple");
        gbc.gridx = xOffset + 532;
        gbc.gridy = 0;
        gbc.gridheight = 1;
        gbc.gridwidth = 1;
        add(twoTupleButton, gbc);

        Image bmpIcon = ImageIO.read(new File(basePath + "resources/icons/icon_bit_map.bmp")).getScaledInstance(41, 41, Image.SCALE_SMOOTH);
        eightBitPerPixelBitMapButton = new JButton(new ImageIcon(bmpIcon));
        eightBitPerPixelBitMapButton.addActionListener(new open_8bpp_BitMap());
        eightBitPerPixelBitMapButton.setPreferredSize(new Dimension(50, 50));
        eightBitPerPixelBitMapButton.setBackground(Color.darkGray);
        eightBitPerPixelBitMapButton.setToolTipText("Linear BitMap");
        gbc.gridy = 1;
        add(eightBitPerPixelBitMapButton, gbc);

        Image byteCloudIcon = ImageIO.read(new File(basePath + "resources/icons/icon_cloud.bmp")).getScaledInstance(41, 41, Image.SCALE_SMOOTH);
        byteCloudButton = new JButton(new ImageIcon(byteCloudIcon));
        byteCloudButton.addActionListener(new open_byte_cloud());
        byteCloudButton.setPreferredSize(new Dimension(50, 50));
        byteCloudButton.setBackground(Color.darkGray);
        byteCloudButton.setToolTipText("Byte Cloud");
        gbc.gridy = 2;
        add(byteCloudButton, gbc);
        
        Image metricMapIcon = ImageIO.read(new File(basePath + "resources/icons/icon_metricMap.png")).getScaledInstance(41, 41, Image.SCALE_SMOOTH);
        metricMapButton = new JButton(new ImageIcon(metricMapIcon));
        metricMapButton.addActionListener(new open_metric_map());
        metricMapButton.setPreferredSize(new Dimension(50, 50));
        metricMapButton.setBackground(Color.darkGray);
        metricMapButton.setToolTipText("Metric Map");
        gbc.gridy = 3;
        add(metricMapButton, gbc);

        Image oneTupleIcon = ImageIO.read(new File(basePath + "resources/icons/icon_1_tuple.bmp")).getScaledInstance(41, 41, Image.SCALE_SMOOTH);
        oneTupleButton = new JButton(new ImageIcon(oneTupleIcon));
        oneTupleButton.addActionListener(new open_one_tuple());
        oneTupleButton.setPreferredSize(new Dimension(50, 50));
        oneTupleButton.setBackground(Color.darkGray);
        oneTupleButton.setToolTipText("One Tuple");
        gbc.gridy = 4;
        add(oneTupleButton, gbc);
        
        themeButton = new JButton("th");
        themeButton.addActionListener(new change_theme());
        gbc.gridy = 5;
        add(themeButton, gbc);

        long maxAddress = macroSlider.getUpperValue(); 
        long minAddress = macroSlider.getValue() - 1L;
        
        macroValueHigh.setText(formatAddressForDisplay(maxAddress));
        macroValueHigh.setHorizontalAlignment(SwingConstants.LEFT);

        macroValueLow.setText(formatAddressForDisplay(minAddress));
        macroValueLow.setHorizontalAlignment(SwingConstants.LEFT);

        maxAddress = microSlider.getUpperValue();
        minAddress = microSlider.getValue() - 1L;
        
        programName.setText(cantordust.name);
        gbc.gridx = xOffset + 0;
        gbc.gridy = 513;
        //add(programName, gbc);

        microValueLow.setText(formatAddressForDisplay(minAddress));
        microValueLow.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx = xOffset + 5;
        gbc.gridwidth = 5;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        add(microValueLow, gbc);

        dataRange.setText("-");
        gbc.gridx = xOffset + 10;
        gbc.gridwidth = 1;
        add(dataRange, gbc);

        microValueHigh.setText(formatAddressForDisplay(maxAddress));
        microValueHigh.setHorizontalAlignment(SwingConstants.LEFT);
        gbc.gridx = xOffset + 11;
        gbc.gridwidth = 5;
        add(microValueHigh, gbc);

        coverageValue.setHorizontalAlignment(SwingConstants.RIGHT);
        coverageValue.setPreferredSize(new Dimension(230, 18));
        gbc.gridx = xOffset + 402;
        gbc.gridwidth = 130;
        gbc.anchor = GridBagConstraints.EAST;
        add(coverageValue, gbc);


        widthValue.setText(Integer.toHexString(widthSlider.getValue()).toUpperCase());
        widthValue.setHorizontalAlignment(SwingConstants.LEFT);

        offsetValue.setText(Integer.toHexString(offsetSlider.getValue()).toUpperCase());
        offsetValue.setHorizontalAlignment(SwingConstants.LEFT);
         
        // Add listener to update display.
        if(dataSlider != null){
            dataSlider.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e) {
                    JSlider slider = (JSlider)e.getSource();
                    if(!suppressDataSliderCallback){
                        updateDataWindow(slider.getValue());
                    }
                }
            });
        }
        macroSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                BitMapSlider slider = (BitMapSlider) e.getSource();
                long maxAddress1 = slider.getUpperValue();
                long minAddress1 = slider.getValue() - 1L;

                // Update text for upper and lower value
                macroValueHigh.setText(formatAddressForDisplay(maxAddress1));
                macroValueLow.setText(formatAddressForDisplay(minAddress1));

                remapMicroSliderToMacroWindow(slider, microSlider);
                if(microSlider.ui != null) {
                    int bitmapLow = Math.max(0, slider.getValue() - 1);
                    int bitmapHigh = Math.min(data.length, slider.getUpperValue());
                    microSlider.ui.makeBitmapAsync(bitmapLow, bitmapHigh);
                }

                // Update text for upper and lower value of microSlider
                if(dataSlider != null){
                    maxAddress1 = dataSlider.getValue() + microSlider.getUpperValue();
                    minAddress1 = dataSlider.getValue() + microSlider.getValue() - 1L;
                    microValueHigh.setText(formatAddressForDisplay(maxAddress1));
                    microValueLow.setText(formatAddressForDisplay(minAddress1));
                } else {
                    maxAddress1 = microSlider.getUpperValue();
                    minAddress1 = microSlider.getValue() - 1L + slider.getValue();
                    microValueHigh.setText(formatAddressForDisplay(maxAddress1));
                    microValueLow.setText(formatAddressForDisplay(minAddress1));
                }
                refreshCoverageIndicator();

                if(slider.getValueIsAdjusting()) {
                    repaint();
                }
            }
        });
        microSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                BitMapSlider slider = (BitMapSlider) e.getSource();
                long maxAddress1 = slider.getUpperValue();
                long minAddress1 = slider.getValue();

                // Update text for the slider
                if(dataSlider != null){
                    // cantordust.cdprint("max"+slider.getMaximum()+"\n");
                    // cantordust.cdprint("min"+slider.getMinimum()+"\n");
                    maxAddress1 = maxAddress1 + dataSlider.getValue();
                    minAddress1 = minAddress1 + dataSlider.getValue();
                    microValueHigh.setText(formatAddressForDisplay(maxAddress1));
                    microValueLow.setText(formatAddressForDisplay(minAddress1));
                } else {
                    microValueHigh.setText(formatAddressForDisplay(maxAddress1));
                    microValueLow.setText(formatAddressForDisplay(minAddress1));
                }
                if(!suppressMicroPreferredTracking) {
                    microPreferredExtent = Math.max(1, slider.getExtent());
                }
                refreshCoverageIndicator();

                if(macroSlider.getValueIsAdjusting()) {
                    repaint();
                }
            }
        });
        widthSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSlider slider = (JSlider) e.getSource();
                widthValue.setText(Integer.toHexString(slider.getValue()).toUpperCase());
            }
        });
        offsetSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                JSlider slider = (JSlider) e.getSource();
                offsetValue.setText(Integer.toHexString(slider.getValue()).toUpperCase());
            }
        });

        // Ensure first-entry slider bitmaps are built from finalized bounds.
        SwingUtilities.invokeLater(() -> {
            if(macroSlider.ui != null) {
                macroSlider.ui.makeBitmapAsync(0, data.length);
            }
            if(microSlider.ui != null) {
                int low = Math.max(0, macroSlider.getValue() - 1);
                int high = Math.min(data.length, macroSlider.getUpperValue());
                microSlider.ui.makeBitmapAsync(low, high);
            }
        });

        refreshCoverageIndicator();
        darkTheme();
    }

    private void addPlaybackControls(GridBagConstraints gbc) {
        Insets originalInsets = gbc.insets;
        int originalGridWidth = gbc.gridwidth;
        int originalGridHeight = gbc.gridheight;
        int originalGridX = gbc.gridx;
        int originalGridY = gbc.gridy;
        int originalFill = gbc.fill;
        int originalAnchor = gbc.anchor;

        playbackPanel = new JPanel();
        playbackPanel.setLayout(new BoxLayout(playbackPanel, BoxLayout.Y_AXIS));
        playbackPanel.setOpaque(false);

        JPanel topRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        topRow.setOpaque(false);

        JPanel bottomRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
        bottomRow.setOpaque(false);

        playbackPlayButton = new JButton("Play");
        playbackPauseButton = new JButton("Pause");
        playbackStopButton = new JButton("Stop");
        playbackLoopCheckBox = new JCheckBox("Loop");
        playbackTargetCombo = new JComboBox<String>();
        if(dataSlider != null) {
            playbackTargetCombo.addItem(DataPlaybackController.PlaybackTarget.ABSOLUTE_WINDOW.getLabel());
        }
        playbackTargetCombo.addItem(DataPlaybackController.PlaybackTarget.MACRO_RANGE.getLabel());
        playbackTargetCombo.addItem(DataPlaybackController.PlaybackTarget.MICRO_RANGE.getLabel());

        playbackTargetLabel.setText("Slider");
        playbackStepSpinner = new JSpinner(new SpinnerNumberModel(64, 1, DATA_WINDOW_SIZE, 1));
        playbackIntervalSpinner = new JSpinner(new SpinnerNumberModel(40, 1, 1000, 1));
        playbackStepLabel.setText("Step");
        playbackIntervalLabel.setText("ms");

        playbackTargetCombo.setPreferredSize(new Dimension(95, 22));
        playbackStepSpinner.setPreferredSize(new Dimension(70, 22));
        playbackIntervalSpinner.setPreferredSize(new Dimension(60, 22));

        topRow.add(playbackPlayButton);
        topRow.add(playbackPauseButton);
        topRow.add(playbackStopButton);
        topRow.add(playbackLoopCheckBox);

        bottomRow.add(playbackTargetLabel);
        bottomRow.add(playbackTargetCombo);
        bottomRow.add(playbackStepLabel);
        bottomRow.add(playbackStepSpinner);
        bottomRow.add(playbackIntervalLabel);
        bottomRow.add(playbackIntervalSpinner);

        playbackPanel.add(topRow);
        playbackPanel.add(bottomRow);

        gbc.gridheight = 1;
        gbc.gridwidth = 120;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridy = 513;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.gridx = xOffset + 20;
        add(playbackPanel, gbc);

        gbc.gridx = originalGridX;
        gbc.gridy = originalGridY;
        gbc.gridwidth = originalGridWidth;
        gbc.gridheight = originalGridHeight;
        gbc.fill = originalFill;
        gbc.anchor = originalAnchor;
        gbc.insets = originalInsets;
    }

    /*public changeDemo() {
        JButton decLowerButton = new JButton("decrease lower bound");
        JButton incLowerButton = new JButton("increase lower bound");
        JButton decUpperButton = new JButton("decrease upper bound");
        JButton incUpperButton = new JButton("increase upper bound");
    }*/
    
    public static int getWindowWidth() {
        return 1180;
    }

    public static int getWindowHeight() {
        return 780;
    }

    public byte[] getData() {
        return this.data;
    }

    public JFrame getFrame() {
        return this.frame;
    }

    private void updateDataWindow(int requestedStart) {
        requestDataWindowUpdate(requestedStart);
    }

    public void applyDataWindowFromPlayback(int requestedStart) {
        if(dataSlider == null) {
            return;
        }

        int clampedStart = clampDataWindowStart(requestedStart);
        if(dataSlider.getValue() != clampedStart) {
            suppressDataSliderCallback = true;
            try {
                dataSlider.setValue(clampedStart);
            } finally {
                suppressDataSliderCallback = false;
            }
        }
        requestDataWindowUpdate(clampedStart);
    }

    public boolean isPlaybackActive() {
        return dataPlaybackController != null && dataPlaybackController.isPlaying();
    }

    public boolean isPlaybackTargetAvailable(DataPlaybackController.PlaybackTarget target) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                return dataSlider != null;
            case MACRO_RANGE:
                return macroSlider != null;
            case MICRO_RANGE:
                return microSlider != null;
            default:
                return false;
        }
    }

    public JSlider getPlaybackSwingSlider(DataPlaybackController.PlaybackTarget target) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                return dataSlider;
            case MACRO_RANGE:
                return macroSlider;
            case MICRO_RANGE:
                return microSlider;
            default:
                return null;
        }
    }

    public int getPlaybackTargetCurrent(DataPlaybackController.PlaybackTarget target) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                return getDataWindowStart();
            case MACRO_RANGE:
                return macroSlider.getValue();
            case MICRO_RANGE:
                return microSlider.getValue();
            default:
                return 0;
        }
    }

    public int getPlaybackTargetMinimum(DataPlaybackController.PlaybackTarget target) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                return getDataWindowMinimum();
            case MACRO_RANGE:
                return macroSlider.getMinimum();
            case MICRO_RANGE:
                return microSlider.getMinimum();
            default:
                return 0;
        }
    }

    public int getPlaybackTargetMaximum(DataPlaybackController.PlaybackTarget target) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                return getDataWindowMaximum();
            case MACRO_RANGE:
                return getRangePlaybackMaxStart(macroSlider);
            case MICRO_RANGE:
                return getRangePlaybackMaxStart(microSlider);
            default:
                return 0;
        }
    }

    public void applyPlaybackTarget(DataPlaybackController.PlaybackTarget target, int requestedStart) {
        switch(target) {
            case ABSOLUTE_WINDOW:
                applyDataWindowFromPlayback(requestedStart);
                break;
            case MACRO_RANGE:
                applyRangeWindowFromPlayback(macroSlider, requestedStart);
                break;
            case MICRO_RANGE:
                applyRangeWindowFromPlayback(microSlider, requestedStart);
                break;
            default:
                break;
        }
    }

    public int getDataWindowStart() {
        if(dataSlider == null) {
            return 0;
        }
        return dataSlider.getValue();
    }

    public int getDataWindowMinimum() {
        if(dataSlider == null) {
            return 0;
        }
        return dataSlider.getMinimum();
    }

    public int getDataWindowMaximum() {
        if(dataSlider == null) {
            return 0;
        }
        return dataSlider.getMaximum();
    }

    private int getRangePlaybackMaxStart(BitMapSlider slider) {
        int maxStart = slider.getMaximum() - slider.getExtent();
        return Math.max(slider.getMinimum(), maxStart);
    }

    private void applyRangeWindowFromPlayback(BitMapSlider slider, int requestedStart) {
        int min = slider.getMinimum();
        int maxStart = getRangePlaybackMaxStart(slider);
        int start = Math.max(min, Math.min(requestedStart, maxStart));
        if(slider.getValue() == start) {
            return;
        }

        slider.getModel().setRangeProperties(start, slider.getExtent(), slider.getMinimum(), slider.getMaximum(), false);
    }

    private int clampDataWindowStart(int requestedStart) {
        int windowSize = Math.min(DATA_WINDOW_SIZE, fullData.length);
        int maxStart = Math.max(0, fullData.length - windowSize);
        return Math.max(0, Math.min(requestedStart, maxStart));
    }

    private int getDataWindowUpdateThrottleMs() {
        if(isPlaybackActive()) {
            return DATA_WINDOW_UPDATE_THROTTLE_MS;
        }
        if(dataSlider != null && dataSlider.getValueIsAdjusting()) {
            return DATA_WINDOW_UPDATE_THROTTLE_MS;
        }
        return 0;
    }

    private byte[] copyDataWindowIntoBuffer(int start, int length) {
        if(length <= 0) {
            return new byte[0];
        }
        byte[] buffer = dataWindowBufferPool[dataWindowBufferCursor];
        if(buffer == null || buffer.length != length) {
            buffer = new byte[length];
            dataWindowBufferPool[dataWindowBufferCursor] = buffer;
        }
        System.arraycopy(fullData, start, buffer, 0, length);
        dataWindowBufferCursor = (dataWindowBufferCursor + 1) % DATA_WINDOW_BUFFER_POOL_SIZE;
        return buffer;
    }

    private void requestDataWindowUpdate(int requestedStart) {
        if(dataSlider == null) {
            return;
        }

        synchronized(dataWindowUpdateLock) {
            pendingDataWindowStart = requestedStart;
            pendingDataWindowRequestId++;
            if(dataWindowUpdateInProgress) {
                return;
            }
            dataWindowUpdateInProgress = true;
        }

        dataWindowExecutor.execute(() -> drainPendingDataWindowUpdates());
    }

    private void drainPendingDataWindowUpdates() {
        long lastApplyStartMs = 0L;
        while(true) {
            long now = System.currentTimeMillis();
            int throttleMs = getDataWindowUpdateThrottleMs();
            long waitMs = throttleMs - (now - lastApplyStartMs);
            if(waitMs > 0L) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    synchronized(dataWindowUpdateLock) {
                        dataWindowUpdateInProgress = false;
                    }
                    return;
                }
            }

            int requestedStart;
            long requestId;
            synchronized(dataWindowUpdateLock) {
                requestedStart = pendingDataWindowStart;
                requestId = pendingDataWindowRequestId;
            }
            lastApplyStartMs = System.currentTimeMillis();

            int start = clampDataWindowStart(requestedStart);
            int windowSize = Math.min(DATA_WINDOW_SIZE, fullData.length);
            int end = Math.min(fullData.length, start + windowSize);
            byte[] windowData = copyDataWindowIntoBuffer(start, end - start);

            final int applyStart = start;
            final byte[] applyData = windowData;
            try {
                SwingUtilities.invokeAndWait(() -> applyDataWindow(applyStart, applyData));
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                synchronized(dataWindowUpdateLock) {
                    dataWindowUpdateInProgress = false;
                }
                return;
            } catch (InvocationTargetException invocationTargetException) {
                cantordust.cdprint("data window update failed\n");
                synchronized(dataWindowUpdateLock) {
                    dataWindowUpdateInProgress = false;
                }
                return;
            }

            synchronized(dataWindowUpdateLock) {
                if(requestId == pendingDataWindowRequestId) {
                    dataWindowUpdateInProgress = false;
                    return;
                }
            }
        }
    }

    private void applyDataWindow(int start, byte[] nextDataWindow) {
        data = nextDataWindow;

        long maxAddress = start + microSlider.getUpperValue();
        long minAddress = start + macroSlider.getValue() + microSlider.getValue() - 1L;
        microValueHigh.setText(formatAddressForDisplay(maxAddress));
        microValueLow.setText(formatAddressForDisplay(minAddress));

        macroSlider.updateData(data);
        microSlider.updateData(data);
        if(macroSlider.ui != null) {
            macroSlider.ui.makeBitmapAsync(0, data.length);
        }
        if(microSlider.ui != null) {
            microSlider.ui.makeBitmapAsync(macroSlider.getValue(), macroSlider.getUpperValue());
        }
        refreshCoverageIndicator();
        requestMetricMapRefresh();
    }

    private void requestMetricMapRefresh() {
        MetricMap.requestRenderAll();
    }

    private long clampOffsetForDisplay(long fileOffset) {
        if(fullData == null || fullData.length <= 0) {
            return Math.max(0L, fileOffset);
        }
        long maxOffset = fullData.length - 1L;
        return Math.max(0L, Math.min(maxOffset, fileOffset));
    }

    private String formatAddressForDisplay(long fileOffset) {
        return cantordust.formatAddressForFileOffset(clampOffsetForDisplay(fileOffset));
    }

    private void remapMicroSliderToMacroWindow(BitMapSlider macro, BitMapSlider micro) {
        int oldMin = micro.getMinimum();
        int oldMax = micro.getMaximum();
        int oldLow = micro.getValue();
        int oldSpan = Math.max(1, micro.getExtent());
        int oldRange = Math.max(1, oldMax - oldMin);

        int newMin = macro.getValue();
        int newMax = Math.max(newMin + 1, macro.getUpperValue());
        int newRange = Math.max(1, newMax - newMin);

        int desiredSpan = microPreferredExtent > 0 ? microPreferredExtent : oldSpan;
        int newSpan = Math.max(1, Math.min(desiredSpan, newRange));

        int oldEffectiveSpan = Math.max(1, Math.min(oldSpan, oldRange));
        int oldSlideRange = Math.max(0, oldRange - oldEffectiveSpan);
        int oldOffset = Math.max(0, Math.min(oldLow - oldMin, oldSlideRange));
        int newSlideRange = Math.max(0, newRange - newSpan);

        int newOffset;
        if(oldSlideRange == 0 || newSlideRange == 0) {
            newOffset = 0;
        } else {
            double offsetRatio = (double)oldOffset / (double)oldSlideRange;
            newOffset = (int)Math.round(offsetRatio * newSlideRange);
        }

        int newLow = newMin + Math.max(0, Math.min(newOffset, newSlideRange));
        suppressMicroPreferredTracking = true;
        try {
            micro.getModel().setRangeProperties(newLow, newSpan, newMin, newMax, micro.getValueIsAdjusting());
        } finally {
            suppressMicroPreferredTracking = false;
        }
    }

    private int getCoveredAddressCount() {
        if(microSlider == null) {
            return 1;
        }
        int low = Math.max(0, microSlider.getValue());
        int high = Math.max(low + 1, microSlider.getUpperValue());
        int covered = Math.max(1, high - low);
        int sourceLength = data != null ? data.length : 0;
        if(sourceLength > 0) {
            covered = Math.min(covered, sourceLength);
        }
        return Math.max(1, covered);
    }

    private void refreshCoverageIndicator() {
        int covered = getCoveredAddressCount();
        coverageValue.setText(String.format("Coverage: %,d addr", covered));
    }

    private GridBagConstraints buildVisualizerConstraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = xOffset + 20;
        gbc.gridy = 0;
        gbc.gridheight = 512;
        gbc.gridwidth = 512;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        return gbc;
    }

    /**
     * Sets the current theme to dark
     */
    private void darkTheme() {
        this.theme = 1;
        setTheme(Color.black, Color.white, Color.darkGray);
    }

    /**
     * Sets the current theme to light
     */
    private void lightTheme() {
        this.theme = 0;
        Color c = UIManager.getColor("panelButtons.background");
        Color textColor = Color.black;
        setTheme(c, textColor, c);
    }

    /**
     * Sets colors of various components
     */
    private void setTheme(Color c, Color textColor, Color buttonColor) {
        this.setBackground(c);

        this.widthSlider.setBackground(c);
        this.offsetSlider.setBackground(c);
        if(this.dataSlider != null){
            this.dataSlider.setBackground(c);
        }

        this.macroSlider.setBackground(c);
        this.microSlider.setBackground(c);

        this.macroValueHigh.setForeground(textColor);
        this.macroValueLow.setForeground(textColor);
        this.microValueHigh.setForeground(textColor);
        this.microValueLow.setForeground(textColor);
        this.widthValue.setForeground(textColor);
        this.offsetValue.setForeground(textColor);
        this.coverageValue.setForeground(textColor);

        this.widthDownButton.setBackground(c);
        this.widthDownButton.setForeground(textColor);

        this.widthUpButton.setBackground(c);
        this.widthUpButton.setForeground(textColor);

        this.offsetDownButton.setBackground(c);
        this.offsetDownButton.setForeground(textColor);

        this.offsetUpButton.setBackground(c);
        this.offsetUpButton.setForeground(textColor);

        this.dataRange.setForeground(textColor);
        this.programName.setForeground(textColor);

        this.microUpButton.setBackground(c);
        this.microUpButton.setForeground(textColor);

        this.themeButton.setBackground(buttonColor);
        this.themeButton.setForeground(textColor);

        if(this.playbackPlayButton != null) {
            this.playbackPlayButton.setBackground(buttonColor);
            this.playbackPlayButton.setForeground(textColor);
        }
        if(this.playbackPauseButton != null) {
            this.playbackPauseButton.setBackground(buttonColor);
            this.playbackPauseButton.setForeground(textColor);
        }
        if(this.playbackStopButton != null) {
            this.playbackStopButton.setBackground(buttonColor);
            this.playbackStopButton.setForeground(textColor);
        }
        if(this.playbackLoopCheckBox != null) {
            this.playbackLoopCheckBox.setBackground(c);
            this.playbackLoopCheckBox.setForeground(textColor);
        }
        if(this.playbackTargetCombo != null) {
            this.playbackTargetCombo.setBackground(c);
            this.playbackTargetCombo.setForeground(textColor);
        }
        if(this.playbackStepSpinner != null) {
            this.playbackStepSpinner.setBackground(c);
            this.playbackStepSpinner.setForeground(textColor);
        }
        if(this.playbackIntervalSpinner != null) {
            this.playbackIntervalSpinner.setBackground(c);
            this.playbackIntervalSpinner.setForeground(textColor);
        }
        if(this.playbackPanel != null) {
            this.playbackPanel.setBackground(c);
            this.playbackPanel.setForeground(textColor);
        }
        this.playbackTargetLabel.setForeground(textColor);
        this.playbackStepLabel.setForeground(textColor);
        this.playbackIntervalLabel.setForeground(textColor);

        if(dispMetricMap) {
            currVis.setBackground(c);
        }
    }

    private class open_one_tuple implements ActionListener {
        open_one_tuple() {
        	
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!(currVis instanceof OneTupleVisualizer)) {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                    currVis.setVisible(false);
                    remove(currVis);
                    dispMetricMap = false;
                    if(!visualizerPanels.containsKey(visualizerMapKeys.ONETUPLE)) {
                        visualizerPanels.put(visualizerMapKeys.ONETUPLE, new OneTupleVisualizer(OneTupleVisualizer.getWindowSize(), cantordust, frame));
                    }
                    currVis = visualizerPanels.get(visualizerMapKeys.ONETUPLE);
                    //currVis = new OneTupleVisualizer(OneTupleVisualizer.getWindowSize(), cantordust, frame);
                    currVis.setPreferredSize(new Dimension(512, 512));
                    currVis.setVisible(true);
                    add(currVis, buildVisualizerConstraints());
                    validate();
                } else {
                    //JOptionPane.showMessageDialog(null, "test", "InfoBox: " + "test", JOptionPane.INFORMATION_MESSAGE);
                    JFrame frame1 = new JFrame("1 Tuple Visualization");
                    OneTupleVisualizer oneTupleVis = new OneTupleVisualizer(OneTupleVisualizer.getWindowSize(), cantordust, frame1);
                    frame1.getContentPane().add(oneTupleVis);
                    frame1.setSize(OneTupleVisualizer.getWindowSize(), OneTupleVisualizer.getWindowSize());
                    //frame.pack();
                    frame1.setVisible(true);
                    frame1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        }
    }

    private class open_two_tuple implements ActionListener {
        open_two_tuple() {

        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!(currVis instanceof TwoTupleVisualizer)) {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                    currVis.setVisible(false);
                    remove(currVis);
                    dispMetricMap = false;
                    if(!visualizerPanels.containsKey(visualizerMapKeys.TWOTUPLE)) {
                        visualizerPanels.put(visualizerMapKeys.TWOTUPLE, new TwoTupleVisualizer(TwoTupleVisualizer.getWindowSize(), cantordust, frame));
                    }
                    currVis = visualizerPanels.get(visualizerMapKeys.TWOTUPLE);
                    //currVis = new OneTupleVisualizer(OneTupleVisualizer.getWindowSize(), cantordust, frame);
                    currVis.setPreferredSize(new Dimension(512, 512));
                    currVis.setVisible(true);
                    add(currVis, buildVisualizerConstraints());
                    validate();
                } else {
                    //JOptionPane.showMessageDialog(null, "test", "InfoBox: " + "test", JOptionPane.INFORMATION_MESSAGE);
                    JFrame frame1 = new JFrame("2 Tuple Visualization");
                    TwoTupleVisualizer twoTupleVis = new TwoTupleVisualizer(TwoTupleVisualizer.getWindowSize(), cantordust, frame1);
                    frame1.getContentPane().add(twoTupleVis);
                    frame1.setSize(TwoTupleVisualizer.getWindowSize(), TwoTupleVisualizer.getWindowSize());
                    //frame.pack();
                    frame1.setVisible(true);
                    frame1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        }
    }

    private class open_8bpp_BitMap implements ActionListener {
        open_8bpp_BitMap() {

        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!(currVis instanceof BitMapVisualizer)) {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                    currVis.setVisible(false);
                    remove(currVis);
                    dispMetricMap = false;
                    if(!visualizerPanels.containsKey(visualizerMapKeys.BITMAP)) {
                        cantordust.cdprint("map does not contain bitmap\n");
                        visualizerPanels.put(visualizerMapKeys.BITMAP, new BitMapVisualizer(BitMapVisualizer.getWindowSize(), cantordust, frame));
                    } else {cantordust.cdprint("map does contain bitmap\n");}
                    currVis = visualizerPanels.get(visualizerMapKeys.BITMAP);
                    currVis.setVisible(true);
                    currVis.setPreferredSize(new Dimension(512, 512));
                    add(currVis, buildVisualizerConstraints());
                    validate();
                } else {
                    JFrame frame1 = new JFrame("Linear Bit Map");
                    BitMapVisualizer bitMapVis = new BitMapVisualizer(BitMapVisualizer.getWindowSize(), cantordust, frame1);
                    frame1.getContentPane().add(bitMapVis);
                    bitMapVis.setColorMapper(new EightBitPerPixelMapper(cantordust));
                    frame1.setSize(BitMapVisualizer.getWindowSize(), BitMapVisualizer.getWindowSize());
                    frame1.setVisible(true);
                    frame1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        }
    }

    private class open_byte_cloud implements ActionListener {
        open_byte_cloud() {
        	
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!(currVis instanceof ByteCloudVisualizer)) {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                    currVis.setVisible(false);
                    remove(currVis);
                    dispMetricMap = false;
                    if(!visualizerPanels.containsKey(visualizerMapKeys.BYTECLOUD)) {
                        visualizerPanels.put(visualizerMapKeys.BYTECLOUD, new ByteCloudVisualizer(ByteCloudVisualizer.getWindowSize(), cantordust));
                    }
                    currVis = visualizerPanels.get(visualizerMapKeys.BYTECLOUD);
                    currVis.setVisible(true);
                    currVis.setPreferredSize(new Dimension(512, 512));
                    add(currVis, buildVisualizerConstraints());
                    validate();
                } else {
                    JFrame frame1 = new JFrame("Byte Cloud Visualization");
                    ByteCloudVisualizer byteCloudVis = new ByteCloudVisualizer(ByteCloudVisualizer.getWindowSize(), cantordust);
                    frame1.getContentPane().add(byteCloudVis);
                    frame1.setSize(ByteCloudVisualizer.getWindowSize(), ByteCloudVisualizer.getWindowSize());
                    frame1.setVisible(true);
                    frame1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        }
    }

    private class open_metric_map implements ActionListener {

        open_metric_map() {
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!(currVis instanceof MetricMap)) {
                if ((e.getModifiers() & ActionEvent.SHIFT_MASK) > 0) {
                    currVis.setVisible(false);
                    remove(currVis);
                    dispMetricMap = false;
                    if(!visualizerPanels.containsKey(visualizerMapKeys.METRIC)) {
                        visualizerPanels.put(visualizerMapKeys.METRIC, new MetricMap(MetricMap.getWindowSize(), cantordust, frame, true));
                    }
                    currVis = visualizerPanels.get(visualizerMapKeys.METRIC);
                    currVis.setPreferredSize(new Dimension(512, 512));
                    currVis.setVisible(true);
                    add(currVis, buildVisualizerConstraints()) ;
                    repaint();
                    validate();
                } else {
                    JFrame frame1 = new JFrame("Metric Map");
                    MetricMap metricMap = new MetricMap(MetricMap.getWindowSize(), cantordust, frame1, false);
                    frame1.getContentPane().add(metricMap);
                    frame1.setSize(MetricMap.getWindowSize(), MetricMap.getWindowSize()+30);
                    frame1.setVisible(true);
                    frame1.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                }
            }
        }
    }

    private class dec_width implements ActionListener {
        dec_width() {
        	
        }
        @Override
        public void actionPerformed(ActionEvent e) {
            widthSlider.setValue(widthSlider.getValue() - 1);
        }
    }
    
    private class inc_width implements ActionListener {
        inc_width() {
        	
        }
    
        @Override
        public void actionPerformed(ActionEvent e) {
            widthSlider.setValue(widthSlider.getValue() + 1);
        }
    }
    
    private class dec_offset implements ActionListener {
        dec_offset() {
        	
        }
    
        @Override
        public void actionPerformed(ActionEvent e) {
            offsetSlider.setValue(offsetSlider.getValue() - 1);
        }
    }
    
    private class inc_offset implements ActionListener {
        inc_offset() {
        	
        }
    
        @Override
        public void actionPerformed(ActionEvent e) {
            offsetSlider.setValue(offsetSlider.getValue() + 1);
        }
    }
    
    private class inc_micro implements ActionListener {
        inc_micro() {
        	
        }
    
        @Override
        public void actionPerformed(ActionEvent e) {
            microSlider.setValue(microSlider.getValue() + 1);
        }
    }

    private class change_theme implements ActionListener {
        change_theme() {

        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if(theme == 0) {
                // Swap to dark theme
                darkTheme();
            } else {
                // Swap to light theme
                lightTheme();
            }
        }
    }
}
