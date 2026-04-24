package resources;

import javax.swing.JSlider;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class DataPlaybackController {
    public enum PlaybackTarget {
        ABSOLUTE_WINDOW("Absolute"),
        MACRO_RANGE("Macro"),
        MICRO_RANGE("Micro");

        private final String label;

        PlaybackTarget(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public static PlaybackTarget fromLabel(String label) {
            for(PlaybackTarget target : PlaybackTarget.values()) {
                if(target.getLabel().equals(label)) {
                    return target;
                }
            }
            return MICRO_RANGE;
        }
    }

    private final MainInterface mainInterface;
    private final Timer playbackTimer;
    private int stepUnits = 64;
    private boolean loopEnabled = false;
    private PlaybackTarget playbackTarget = PlaybackTarget.MICRO_RANGE;

    public DataPlaybackController(MainInterface mainInterface) {
        this.mainInterface = mainInterface;
        this.playbackTimer = new Timer(40, e -> tickPlayback());
        this.playbackTimer.setInitialDelay(0);
        this.playbackTimer.setCoalesce(true);
    }

    public void play() {
        if(playbackTimer.isRunning()) {
            return;
        }
        if(!mainInterface.isPlaybackTargetAvailable(playbackTarget)) {
            return;
        }

        int forwardBoundary = getForwardBoundaryValue();
        if(mainInterface.getPlaybackTargetCurrent(playbackTarget) == forwardBoundary && !loopEnabled) {
            mainInterface.applyPlaybackTarget(playbackTarget, getStartBoundaryValue());
        }
        playbackTimer.start();
    }

    public void pause() {
        playbackTimer.stop();
    }

    public void stop() {
        playbackTimer.stop();
        if(mainInterface.isPlaybackTargetAvailable(playbackTarget)) {
            mainInterface.applyPlaybackTarget(playbackTarget, getStartBoundaryValue());
        }
    }

    public boolean isPlaying() {
        return playbackTimer.isRunning();
    }

    public void setLoopEnabled(boolean loopEnabled) {
        this.loopEnabled = loopEnabled;
    }

    public void setTarget(PlaybackTarget target) {
        if(target == null) {
            return;
        }

        if(!mainInterface.isPlaybackTargetAvailable(target)) {
            if(mainInterface.isPlaybackTargetAvailable(PlaybackTarget.MICRO_RANGE)) {
                playbackTarget = PlaybackTarget.MICRO_RANGE;
            } else if(mainInterface.isPlaybackTargetAvailable(PlaybackTarget.MACRO_RANGE)) {
                playbackTarget = PlaybackTarget.MACRO_RANGE;
            } else {
                playbackTarget = PlaybackTarget.ABSOLUTE_WINDOW;
            }
            return;
        }
        playbackTarget = target;
    }

    public PlaybackTarget getTarget() {
        return playbackTarget;
    }

    public void setStepUnits(int stepUnits) {
        this.stepUnits = Math.max(1, stepUnits);
    }

    public void setIntervalMs(int intervalMs) {
        int safeDelay = Math.max(5, intervalMs);
        playbackTimer.setDelay(safeDelay);
        playbackTimer.setInitialDelay(0);
    }

    private void tickPlayback() {
        if(!mainInterface.isPlaybackTargetAvailable(playbackTarget)) {
            pause();
            return;
        }

        int current = mainInterface.getPlaybackTargetCurrent(playbackTarget);
        int min = mainInterface.getPlaybackTargetMinimum(playbackTarget);
        int max = mainInterface.getPlaybackTargetMaximum(playbackTarget);
        int effectiveStep = Math.max(1, Math.min(stepUnits, Math.max(1, max - min)));
        int next = current + (getForwardDirection() * effectiveStep);

        if(next > max) {
            if(loopEnabled) {
                next = min;
            } else {
                next = max;
                pause();
            }
        } else if(next < min) {
            if(loopEnabled) {
                next = max;
            } else {
                next = min;
                pause();
            }
        }

        mainInterface.applyPlaybackTarget(playbackTarget, next);
    }

    private int getForwardDirection() {
        JSlider slider = mainInterface.getPlaybackSwingSlider(playbackTarget);
        if(slider == null) {
            return 1;
        }
        if(slider.getOrientation() == SwingConstants.VERTICAL) {
            return slider.getInverted() ? 1 : -1;
        }
        return slider.getInverted() ? -1 : 1;
    }

    private int getStartBoundaryValue() {
        int min = mainInterface.getPlaybackTargetMinimum(playbackTarget);
        int max = mainInterface.getPlaybackTargetMaximum(playbackTarget);
        return getForwardDirection() > 0 ? min : max;
    }

    private int getForwardBoundaryValue() {
        int min = mainInterface.getPlaybackTargetMinimum(playbackTarget);
        int max = mainInterface.getPlaybackTargetMaximum(playbackTarget);
        return getForwardDirection() > 0 ? max : min;
    }
}
