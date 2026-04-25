package resources;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JSlider;
import javax.swing.SwingUtilities;

/**
 * UI delegate for the BitMapSlider component. BitMapSliderUI paints two thumbs,
 * one for the lower value and one for the upper value.
 */
class BitMapSliderUI extends RangeSliderUI {
    private volatile BufferedImage img;
    private final Object bitmapRequestLock = new Object();
    private final ExecutorService bitmapExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread worker = new Thread(r, "cantordust-slider-bitmap");
        worker.setDaemon(true);
        return worker;
    });
    private int[] bitmapPixels = new int[0];
    private int pendingLow = 0;
    private int pendingHigh = 0;
    private boolean bitmapWorkerRunning = false;

    public BitMapSliderUI(BitMapSlider b) {
        super(b);
        SwingUtilities.invokeLater(() -> {
            int low = ((BitMapSlider) this.slider).getValue() - 1;
            int high = ((BitMapSlider) this.slider).getUpperValue();
            makeBitmapAsync(low, high);
        });
    }
    
    /**
     * Returns the size of a thumb.
     */
    @Override
    protected Dimension getThumbSize() {
        return new Dimension(100, 10);
    }
 
    /**
     * Creates a listener to handle track events in the specified slider.
     */
    @Override
    protected TrackListener createTrackListener(JSlider slider1) {
        return new BitMapTrackListener();
    }

    /**
     * Paints the track.
     */
    @Override
    public void paintTrack(Graphics g) {
        // Draw track.
        Rectangle trackBounds = trackRect;

        if (img != null) {
            g.drawImage(img, 0, 5, trackBounds.width + 50, trackBounds.height, null);
        }
    }

    /**
     * Makes a bitmap in a new thread, this makes it so updating the slider does not cause everything else to hang
     */
    public void makeBitmapAsync(int low, int high) {
        synchronized(bitmapRequestLock) {
            pendingLow = low;
            pendingHigh = high;
            if(bitmapWorkerRunning) {
                return;
            }
            bitmapWorkerRunning = true;
        }

        bitmapExecutor.execute(() -> processBitmapRequests());
    }

    private void processBitmapRequests() {
        while(true) {
            int low;
            int high;
            synchronized(bitmapRequestLock) {
                low = pendingLow;
                high = pendingHigh;
            }

            if(((BitMapSlider) this.slider).data != null) {
                makeBitmap(low, high);
            }

            synchronized(bitmapRequestLock) {
                if(low == pendingLow && high == pendingHigh) {
                    bitmapWorkerRunning = false;
                    return;
                }
            }
        }
    }

    /**
     * Code that actually makes the bitmap
     */
    private void makeBitmap(int low, int high) {
        BitMapSlider bitMapSlider = (BitMapSlider) this.slider;
        MainInterface mainInterface = bitMapSlider.cd != null ? bitMapSlider.cd.getMainInterface() : null;
        byte[] data = bitMapSlider.data;
        if(data == null || data.length == 0) {
            return;
        }

        // Check if low or high are out of range
        if (high > data.length) {
            high = data.length;
        }

        if (low < 0) {
            low = 0;
        }
        if (low >= data.length) {
            low = data.length - 1;
        }
        if (high <= low) {
            high = Math.min(data.length, low + 1);
        }

        // Calculate width and height
        int width = (mainInterface != null && mainInterface.isPlaybackActive()) ? 200 : 400;
        int height = (high-low)/width-1 > 0? (high-low)/width-1 : 1;

        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int pixelCount = width * height;
        if(bitmapPixels.length != pixelCount) {
            bitmapPixels = new int[pixelCount];
        }
        int count = low;
        for(int pixelIndex = 0; pixelIndex < pixelCount; pixelIndex++) {
            count = count + 1 < data.length - 1 ? count + 1 : data.length - 1;
            bitmapPixels[pixelIndex] = ((data[count] & 0xff) << 8);
        }
        img.setRGB(0, 0, width, height, bitmapPixels, 0, width);

        SwingUtilities.invokeLater(() -> this.slider.repaint());
    }

    /**
     * Paints the thumb for the lower value using the specified graphics object.
     */
    @Override
    protected void paintLowerThumb(Graphics g) {
        Rectangle knobBounds = thumbRect;
        int w = knobBounds.width;    
        
        // Create graphics copy.
        Graphics2D g2d = (Graphics2D) g.create();

        // Create default thumb shape.
        Shape thumbShape = createThumbShape(w - 1, 7);

        // Draw thumb.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.translate(knobBounds.x, knobBounds.y);

        g2d.setColor(Color.gray);
        g2d.fill(thumbShape);

        g2d.setColor(Color.gray);
        g2d.draw(thumbShape);
        
        g2d.setColor(Color.black);
        g2d.fillPolygon(new int[]{(w-1)/2, (w-1)/2-4, (w-1)/2+4}, new int[]{2, 6, 6}, 3);

        // Dispose graphics.
        g2d.dispose();
    }
    
    /**
     * Paints the thumb for the upper value using the specified graphics object.
     */
    @Override
    protected void paintUpperThumb(Graphics g) {
        Rectangle knobBounds = upperThumbRect;
        int w = knobBounds.width;
        
        // Create graphics copy.
        Graphics2D g2d = (Graphics2D) g.create();

        // Create default thumb shape.
        Shape thumbShape = createThumbShape(w - 1, 7);

        // Draw thumb.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.translate(knobBounds.x, knobBounds.y);

        g2d.setColor(Color.gray);
        g2d.fill(thumbShape);

        g2d.setColor(Color.gray);
        g2d.draw(thumbShape);

        g2d.setColor(Color.black);
        g2d.fillPolygon(new int[]{(w-1)/2, (w-1)/2-4, (w-1)/2+4}, new int[]{6, 2, 2}, 3);

        // Dispose graphics.
        g2d.dispose();
    }


    /**
     * Returns a Shape representing a thumb.
     */
    @Override
    public Shape createThumbShape(int width, int height) {
        // Use circular shape.
        Rectangle shape = new Rectangle(width, height);
        return shape;
    }
    
    /**
     * Listener to handle mouse movements in the slider track.
     */

    public class BitMapTrackListener extends RangeSliderUI.RangeTrackListener {
        private boolean windowSliding;
        private double previousY;
        private int lastWindowSlideValue = Integer.MIN_VALUE;

        private boolean isBetweenThumbs(int x, int y) {
            if(thumbRect.contains(x, y) || upperThumbRect.contains(x, y)) {
                return false;
            }
            int gapStart = Math.min(thumbRect.y + thumbRect.height, upperThumbRect.y + upperThumbRect.height);
            int gapEnd = Math.max(thumbRect.y, upperThumbRect.y);
            return y > gapStart && y < gapEnd;
        }

        private void updateRectanglesForSlidingWindow(MouseEvent e) {
            if(windowSliding) {
                double diff = previousY - e.getY();
                double oldY = previousY;
                previousY = e.getY();
                int upperThumbRectNewY = (int)(upperThumbRect.getY() - diff);
                int thumbRectNewY = (int)(thumbRect.getY() - diff);
                if(upperThumbRectNewY <= yPositionForValue(slider.getMaximum()) && thumbRectNewY >= yPositionForValue(slider.getMinimum())) {
                    upperThumbRect.setLocation((int)(upperThumbRect.getX()), upperThumbRectNewY);
                    thumbRect.setLocation((int)(thumbRect.getX()), thumbRectNewY);
                    int thumbMiddle = thumbRectNewY + (thumbRect.height / 2);
                    int newVal = valueForYPosition(thumbMiddle);
                    if(newVal != lastWindowSlideValue) {
                        ((BitMapSlider)slider).getModel().setRangeProperties(newVal, slider.getExtent(), slider.getMinimum(),
                                slider.getMaximum(), true);
                        lastWindowSlideValue = newVal;
                    }
                    slider.repaint();
                    slider.setCursor(new Cursor(e.getY() > oldY ? Cursor.S_RESIZE_CURSOR: Cursor.N_RESIZE_CURSOR));
                }
            }
        }

        private void updateValuesForSlidingWindow(MouseEvent e) {
            if(windowSliding) {
                int thumbMiddle = (int)(thumbRect.getY() + (thumbRect.getHeight() / 2.0));
                int newVal = valueForYPosition(thumbMiddle);
                ((BitMapSlider)slider).getModel().setRangeProperties(newVal, slider.getExtent(), slider.getMinimum(),
                        slider.getMaximum(), slider.getValueIsAdjusting());
                windowSliding = false;
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if(windowSliding) {
                updateRectanglesForSlidingWindow(e);
                return;
            }
            super.mouseDragged(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            updateValuesForSlidingWindow(e);

            lowerDragging = false;
            upperDragging = false;
            slider.setValueIsAdjusting(false);
            slider.setCursor(new Cursor(Cursor.HAND_CURSOR));
            BitMapSlider currentSlider = (BitMapSlider) slider;
            MainInterface mainInterface = currentSlider.cd != null ? currentSlider.cd.getMainInterface() : null;
            if(mainInterface != null && currentSlider == mainInterface.macroSlider && mainInterface.microSlider != null
                    && mainInterface.microSlider.getUI() instanceof BitMapSliderUI) {
                int low = currentSlider.getValue()-1;
                int high = currentSlider.getUpperValue();
                ((BitMapSliderUI) mainInterface.microSlider.getUI()).makeBitmapAsync(low, high);
            }

            super.mouseReleased(e);
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            // Get the X and Y
            int x = e.getX();
            int y = e.getY();

            // double uy = upperThumbRect.getY();
            // double uh = upperThumbRect.getHeight();

            // double ly = thumbRect.getY();
            // double lh = thumbRect.getHeight();

            // Check if the cursor is over or not over one of the slider rectangles
            boolean upperHover = false;
            boolean lowerHover = false;
            if (upperThumbSelected || slider.getMinimum() == slider.getValue()) {
                if (upperThumbRect.contains(x, y)) {
                    upperHover = true;
                } else if (thumbRect.contains(x, y)) {
                    lowerHover = true;
                }
            } else {
                if (thumbRect.contains(x, y)) {
                    lowerHover = true;
                } else if (upperThumbRect.contains(x, y)) {
                    upperHover = true;
                }
            }

            if(upperHover || lowerHover) {
                slider.setCursor(new Cursor(Cursor.HAND_CURSOR));
            } else {
                slider.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }
            if(isBetweenThumbs(x, y)) {
                slider.setCursor(new Cursor(Cursor.HAND_CURSOR));
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            // Get the X and Y
            int x = e.getX();
            int y = e.getY();

            if(isBetweenThumbs(x, y)) {
                windowSliding = true;
                previousY = y;
                slider.setValueIsAdjusting(true);
                lowerDragging = true;
                upperDragging = false;
                lastWindowSlideValue = Integer.MIN_VALUE;
                return;
            }

            // double uy = upperThumbRect.getY();
            // double uh = upperThumbRect.getHeight();

            // double ly = thumbRect.getY();
            // double lh = thumbRect.getHeight();

            // Check if the cursor is over or not over one of the slider rectangles
            boolean upperPressed = false;
            boolean lowerPressed = false;
            if (upperThumbSelected || slider.getMinimum() == slider.getValue()) {
                if (upperThumbRect.contains(x, y)) {
                    upperPressed = true;
                } else if (thumbRect.contains(x, y)) {
                    lowerPressed = true;
                }
            } else {
                if (thumbRect.contains(x, y)) {
                    lowerPressed = true;
                } else if (upperThumbRect.contains(x, y)) {
                    upperPressed = true;
                }
            }

            if(upperPressed || lowerPressed) {
                slider.setCursor(new Cursor(Cursor.MOVE_CURSOR));
            } else {
                slider.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }

            super.mousePressed(e);
        }
    }
}