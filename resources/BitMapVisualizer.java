package resources;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class BitMapVisualizer extends Visualizer {
    private JSlider dataWidthSlider;
    private JSlider dataOffsetSlider;
    private JButton dataWidthDownButton;
    private JButton dataWidthUpButton;
    private JButton dataOffsetDownButton;
    private JButton dataOffsetUpButton;
    private JButton dataMicroUpButton;
    private int mode;
    private final Object renderRequestLock = new Object();
    private final ExecutorService renderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "cantordust-bitmap-render");
        worker.setDaemon(true);
        return worker;
    });
    private boolean renderWorkerRunning = false;
    private int pendingRenderGeneration = 0;
    private final ColorEntropy entropySource;

    private volatile BufferedImage img;

    public BitMapVisualizer(int windowSize, GhidraSrc cantordust, JFrame frame) {
        super(windowSize, cantordust);
        MainInterface mainInterface = cantordust.getMainInterface();
        dataWidthSlider = mainInterface.widthSlider;
        dataOffsetSlider = mainInterface.offsetSlider;
        dataWidthDownButton = mainInterface.widthDownButton;
        dataWidthUpButton = mainInterface.widthUpButton;
        dataOffsetDownButton = mainInterface.offsetDownButton;
        dataOffsetUpButton = mainInterface.offsetUpButton;
        dataMicroUpButton = mainInterface.microUpButton;
        mode = 0;
        this.img = null;
        this.entropySource = new ColorEntropy(cantordust, mainInterface.getData());
        createPopupMenu(frame);

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

        dataWidthSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                constructImageAsync();
            }
        });
        dataOffsetSlider.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) {
                constructImageAsync();
            }
        });
        dataWidthDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                constructImageAsync();
            }
        });
        dataWidthUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                constructImageAsync();
            }
        });
        dataOffsetDownButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                constructImageAsync();
            }
        });
        dataOffsetUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                constructImageAsync();
            }
        });        
        dataMicroUpButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                constructImageAsync();
            }
        });

        this.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                constructImageAsync();
            }
        });

        SwingUtilities.invokeLater(() -> constructImageAsync());
    }
    
    public void createPopupMenu(JFrame frame){
        JPopupMenu popup = new JPopupMenu("test1");
        JMenuItem bpp_8 = new JMenuItem("8bpp");
        bpp_8.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                mode = 0;
                constructImageAsync();
            }
        });
        popup.add(bpp_8);
        
        JMenuItem argb_32 = new JMenuItem("32bpp ARGB");
        argb_32.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                mode = 1;
                constructImageAsync();
            }
        });
        popup.add(argb_32);
        
        JMenuItem bpp_24 = new JMenuItem("24bpp RGB");
        bpp_24.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                mode = 2;
                constructImageAsync();
            }
        });
        popup.add(bpp_24);

        JMenuItem bpp_16 = new JMenuItem("16bpp ARGB1555");
        bpp_16.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                mode = 3;
                constructImageAsync();
            }
        });
        popup.add(bpp_16);

        JMenuItem entropy = new JMenuItem("Entropy");
        entropy.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {    
                mode = 4;
                constructImageAsync();
            }
        });
        popup.add(entropy);

        JCheckBoxMenuItem interpolationToggle = new JCheckBoxMenuItem("Interpolation", RenderSettings.isInterpolationEnabled());
        interpolationToggle.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                RenderSettings.setInterpolationEnabled(interpolationToggle.isSelected());
                repaint();
            }
        });
        popup.add(interpolationToggle);

        this.addMouseListener(new MouseAdapter() {  
            public void mouseReleased(MouseEvent e) {  
                if(e.getButton() == 3){
                    popup.show(frame, BitMapVisualizer.this.getX() + e.getX(), BitMapVisualizer.this.getY() + e.getY());
                }
            }                 
        });  

        this.add(popup);
    }
        
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        if(img != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            RenderSettings.applyImageRenderingHints(g2);
            g2.drawImage(img, 0, 0, Math.max(1, getWidth()), Math.max(1, getHeight()), this);
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
        final int[] renderBounds = new int[4];
        final byte[][] dataHolder = new byte[1][];

        Runnable captureState = () -> {
            dataMicroSlider.setMinimum(dataMacroSlider.getValue());
            dataMicroSlider.setMaximum(dataMacroSlider.getUpperValue());

            byte[] currentData = mainInterface.getData();
            dataHolder[0] = currentData;
            int dataLength = currentData.length;
            int low = Math.max(0, Math.min(dataMicroSlider.getValue(), Math.max(0, dataLength - 1)));
            int high = Math.max(low + 1, Math.min(dataMicroSlider.getUpperValue(), dataLength));

            renderBounds[0] = low;
            renderBounds[1] = high;
            renderBounds[2] = Math.max(1, dataWidthSlider.getValue());
            renderBounds[3] = Math.max(0, dataOffsetSlider.getValue());
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

        byte[] data = dataHolder[0];
        if(data == null || data.length == 0) {
            return;
        }

        int low = renderBounds[0];
        int high = renderBounds[1];
        int width = renderBounds[2];
        int offset = Math.min(renderBounds[3], Math.max(0, data.length - 1));
        int xMax = Math.max(1, width);
        int i = 0;

        BufferedImage bimg;
        int[] pixelBuffer;
        int pixelPos;
        int maxPixels;

        switch(mode) {
            //32bpp ARGB
            case 1:
                bimg = new BufferedImage(width, Math.max(1, (high - low) / xMax / 4 + 1), BufferedImage.TYPE_INT_ARGB);
                pixelBuffer = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
                pixelPos = 0;
                maxPixels = pixelBuffer.length;
                for(i = low; i < high - 4; i += 4) {
                    if(pixelPos >= maxPixels) {
                        break;
                    }
                    int b0 = byteAtOffset(data, i, offset);
                    int b1 = byteAtOffset(data, i + 1, offset);
                    int b2 = byteAtOffset(data, i + 2, offset);
                    int b3 = byteAtOffset(data, i + 3, offset);
                    pixelBuffer[pixelPos++] = (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
                }
                break;

            //24bpp RGB
            case 2:
                bimg = new BufferedImage(width, Math.max(1, (high - low) / xMax / 3 + 1), BufferedImage.TYPE_INT_ARGB);
                pixelBuffer = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
                pixelPos = 0;
                maxPixels = pixelBuffer.length;
                for(i = low; i < high - 3; i += 3) {
                    if(pixelPos >= maxPixels) {
                        break;
                    }
                    int b0 = byteAtOffset(data, i, offset);
                    int b1 = byteAtOffset(data, i + 1, offset);
                    int b2 = byteAtOffset(data, i + 2, offset);
                    pixelBuffer[pixelPos++] = (0xFF << 24) | (b2 << 16) | (b1 << 8) | b0;
                }
                break;

            //16bpp ARGB1555 color is too saturated
            case 3:
                bimg = new BufferedImage(width, Math.max(1, (high - low) / xMax / 2 + 1), BufferedImage.TYPE_INT_ARGB);
                pixelBuffer = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
                pixelPos = 0;
                maxPixels = pixelBuffer.length;
                for(i = low; i < high - 2; i += 2) {
                    if(pixelPos >= maxPixels) {
                        break;
                    }
                    int lowByte = byteAtOffset(data, i, offset);
                    int highByte = byteAtOffset(data, i + 1, offset);
                    int alpha = ((highByte & 0x80) != 0) ? 0xFF : 0x00;
                    int red = (int)((((highByte & 0x7C) >> 2) / 31.0f) * 255.0f);
                    int green = (int)(((((highByte & 0x03) << 3) + ((lowByte & 0xE0) >> 5)) / 31.0f) * 255.0f);
                    int blue = (int)(((lowByte & 0x1F) / 31.0f) * 255.0f);
                    pixelBuffer[pixelPos++] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                }
                break;

            // entropy
            case 4:
                bimg = new BufferedImage(width, Math.max(1, (high - low) / xMax + 1), BufferedImage.TYPE_INT_ARGB);
                pixelBuffer = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
                pixelPos = 0;
                maxPixels = pixelBuffer.length;
                entropySource.setData(data);
                for(i = low; i < high; i++) {
                    if(pixelPos >= maxPixels) {
                        break;
                    }
                    Rgb rgb = entropySource.getPoint(i);
                    pixelBuffer[pixelPos++] = (0xFF << 24) | (rgb.r << 16) | (rgb.g << 8) | rgb.b;
                }
                break;

            // 8bpp
            default:
                bimg = new BufferedImage(width, Math.max(1, (high - low) / xMax + 1), BufferedImage.TYPE_INT_ARGB);
                pixelBuffer = ((DataBufferInt)bimg.getRaster().getDataBuffer()).getData();
                pixelPos = 0;
                maxPixels = pixelBuffer.length;
                for(i = low; i < high; i++) {
                    if(pixelPos >= maxPixels) {
                        break;
                    }
                    int unsignedByte = byteAtOffset(data, i, offset);
                    pixelBuffer[pixelPos++] = (0xFF << 24) | (unsignedByte << 8);
                }
        }

        SwingUtilities.invokeLater(() -> {
            img = bimg;
            repaint();
        });
    }

    private int byteAtOffset(byte[] data, int index, int offset) {
        int sourceIndex = index + offset;
        if(sourceIndex < 0 || sourceIndex >= data.length) {
            return 0;
        }
        return data[sourceIndex] & 0xFF;
    }
    
    public static int getWindowSize() {
        return 800;
    }
}
