package com.storyforge.orchestrator.host;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The "subscribe" pop-up in the bottom-right corner (render-svc SubscribeNudge.jsx, glass style with the channel
 * logo): about one minute in, in the middle and near the end, moved to a moment where nothing else is in that
 * corner or on the whole screen (the masked host, chapter titles, text cards, other overlays). The audio mix
 * plans it (it places the swoosh, click and bell); the assemble stage draws it at the same times.
 */
@Component
public class SubscribeNudge {

    /** Overlay kind in the EDL. */
    public static final String KIND = "subscribe";
    /** In audio_mix: the planned start times. */
    public static final String PLAN = "subscribe.json";
    /** In assemble: the channel logo the pop-up shows. */
    public static final String LOGO = "subscribe_logo.jpg";
    /** Matches SubscribeNudge.jsx: NUDGE_FRAMES (135) at 30 fps; the click on frame 66, the bell 6 frames later. */
    public static final double SECONDS = 4.5;
    public static final double ENTER_AT = 0.25;
    public static final double CLICK_AT = 2.2;
    public static final double BELL_AT = 2.4;
    public static final double EXIT_AT = 3.95;

    static final double FIRST = 60;
    static final double LAST_BEFORE_END = 75;
    static final double EDGE = 8;
    static final double OUTRO = 6;
    static final double PAD = 1;
    static final double SEARCH = 30;
    static final double MIN_GAP = 60;
    static final double MIN_VIDEO = 120;

    public record Plan(List<Double> starts) {
    }

    private final boolean enabled;
    private final Path logo;

    public SubscribeNudge(@Value("${storyforge.subscribe.enabled:true}") boolean enabled,
                          @Value("${storyforge.character.dir:../assets/character}") String characterDir) {
        this.enabled = enabled;
        this.logo = Path.of(characterDir).toAbsolutePath().resolve("logochannel.jpg");
    }

    /** On, and the channel logo is there. */
    public boolean available() {
        return enabled && Files.isRegularFile(logo);
    }

    public Path logo() {
        return logo;
    }

    /**
     * Start times: around 1 minute, the middle and 75 s before the end, each moved by up to 30 s to the nearest
     * moment where its 4.5 s stay clear of {@code busy} ([start, end] seconds) with a second to spare.
     */
    public static List<Double> plan(double duration, List<double[]> busy) {
        List<Double> starts = new ArrayList<>();
        if (duration < MIN_VIDEO) {
            return starts;
        }
        for (double target : new double[] {FIRST, duration / 2, duration - LAST_BEFORE_END}) {
            for (int step = 0; step <= SEARCH * 4; step++) {
                double t = target + ((step + 1) / 2) * 0.5 * (step % 2 == 1 ? 1 : -1);
                if (free(t, duration, busy, starts)) {
                    starts.add(Math.round(t * 1000) / 1000.0);
                    break;
                }
            }
        }
        return starts;
    }

    static boolean free(double t, double duration, List<double[]> busy, List<Double> taken) {
        if (t < EDGE || t + SECONDS > duration - OUTRO) {
            return false;
        }
        for (double[] b : busy) {
            if (t < b[1] + PAD && t + SECONDS > b[0] - PAD) {
                return false;
            }
        }
        for (double s : taken) {
            if (Math.abs(s - t) < MIN_GAP) {
                return false;
            }
        }
        return true;
    }
}
