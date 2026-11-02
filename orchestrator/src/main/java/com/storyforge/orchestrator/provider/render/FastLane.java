package com.storyforge.orchestrator.provider.render;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The fast lane of the long-video render. Remotion draws every frame in a browser (~6-12 frames/s here), but most
 * of a video is a plain shot: a film clip, a host clip or a picture with a slow camera move. FFmpeg draws those at
 * 40-80 frames/s. This class splits the timeline into
 * <ul>
 *   <li>designed ranges for Remotion: text cards, maps, photos, chapter cards, overlays, the talking logo, the
 *       first frames after every non-cut transition and the outro fade;</li>
 *   <li>plain pieces for FFmpeg, each one shot (split at its punch-in), with the same camera move as the
 *       Remotion components (MediaShot / KenBurnsImage / PunchIn) so the two lanes join without a jump.</li>
 * </ul>
 * The film look (grain, vignette) is added once to the whole video at the end.
 */
public final class FastLane {

    public record Range(int from, int to, boolean designed) {
        public int frames() {
            return to - from;
        }
    }

    /** One plain shot piece: frames [from, to) of the video; {@code punched}: the punch-in is already active. */
    public record Piece(EdlShot shot, int from, int to, boolean punched, int shotFrom, int shotFrames) {
        public int frames() {
            return to - from;
        }
    }

    /** Must match render-svc: Transitions.jsx OVERLAP/TRANSITION_FRAMES, LongForm CHAPTER/OUTRO frames. */
    static final int OVERLAP_FRAMES = 10;
    static final int TRANSITION_FRAMES = 20;
    static final int CHAPTER_FRAMES = 105;
    static final int OUTRO_FRAMES = 15;
    /** Gaps shorter than this between two designed ranges are drawn by Remotion too (fewer joins). */
    static final int MERGE_GAP = 24;
    static final Set<String> PLAIN_MEDIA = Set.of("clip", "footage", "host", "ai");
    static final Set<String> GRAPHIC_TYPES = Set.of("kinetic_text", "map", "timeline", "chart");

    /** Ken Burns moves, as in KenBurnsImage.jsx: scale, x %, y % from -> to. */
    private static final Map<String, double[]> MOTIONS = Map.of(
            "push_in", new double[] {1.02, 1.16, 0, 0.6, 0, -1.2},
            "pull_out", new double[] {1.16, 1.03, 0.8, 0, -1.0, 0},
            "pan_left", new double[] {1.12, 1.14, 3.5, -3.5, 0, -0.4},
            "pan_right", new double[] {1.12, 1.14, -3.5, 3.5, 0, 0.4},
            "none", new double[] {1.04, 1.06, 0, 0, 0, 0});

    /** CSS filters of MediaShot.jsx / KenBurnsImage.jsx as eq: contrast, saturation, brightness (additive). */
    private static final Map<String, double[]> GRADES = Map.of(
            "clip", new double[] {1.08, 0.95, 0},
            "footage", new double[] {1.08, 0.85, -0.025},
            "host", new double[] {1.05, 1.02, 0},
            "ai", new double[] {1.06, 0.95, 0});

    private FastLane() {
    }

    /** The whole timeline as alternating designed (Remotion) and plain (FFmpeg) ranges. */
    public static List<Range> plan(Edl edl) {
        int fps = edl.fps();
        int total = (int) Math.round(edl.durationSec() * fps);
        if (edl.style() != null && edl.style().burnCaptions()) {
            return List.of(new Range(0, total, true));
        }
        List<int[]> windows = new ArrayList<>();
        List<EdlShot> shots = edl.shots();
        for (int i = 0; i < shots.size(); i++) {
            EdlShot shot = shots.get(i);
            int start = frame(shot.start(), fps);
            int end = frame(shot.end(), fps);
            if (GRAPHIC_TYPES.contains(shot.type()) || !PLAIN_MEDIA.contains(shot.media())) {
                windows.add(new int[] {start, end});
            }
            if (i > 0 && shot.transition() != null && !"cut".equals(shot.transition())) {
                windows.add(new int[] {start, start + TRANSITION_FRAMES});
            }
        }
        if (edl.chapters() != null) {
            edl.chapters().stream().skip(1)
                    .forEach(c -> windows.add(new int[] {frame(c.start(), fps), frame(c.start(), fps) + CHAPTER_FRAMES}));
        }
        if (edl.overlays() != null) {
            edl.overlays().forEach(o -> windows.add(new int[] {frame(o.start(), fps), frame(o.end(), fps)}));
        }
        if (edl.host() != null && edl.host().appearances() != null) {
            edl.host().appearances().forEach(a -> windows.add(new int[] {frame(a.start(), fps), frame(a.end(), fps)}));
        }
        windows.add(new int[] {total - OUTRO_FRAMES, total});
        windows.sort(Comparator.comparingInt(w -> w[0]));
        List<int[]> merged = new ArrayList<>();
        for (int[] w : windows) {
            int from = Math.max(0, w[0]);
            int to = Math.min(total, w[1]);
            if (to <= from) {
                continue;
            }
            int[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && from - last[1] < MERGE_GAP) {
                last[1] = Math.max(last[1], to);
            } else {
                merged.add(new int[] {from, to});
            }
        }
        if (!merged.isEmpty() && merged.get(0)[0] < MERGE_GAP) {
            merged.get(0)[0] = 0;
        }
        List<Range> ranges = new ArrayList<>();
        int cursor = 0;
        for (int[] w : merged) {
            if (w[0] > cursor) {
                ranges.add(new Range(cursor, w[0], false));
            }
            ranges.add(new Range(w[0], w[1], true));
            cursor = w[1];
        }
        if (cursor < total) {
            ranges.add(new Range(cursor, total, false));
        }
        return ranges;
    }

    /** The plain shots inside a plain range, split where a punch-in starts. */
    public static List<Piece> pieces(Edl edl, Range range) {
        int fps = edl.fps();
        List<Piece> pieces = new ArrayList<>();
        for (EdlShot shot : edl.shots()) {
            int start = frame(shot.start(), fps);
            int end = frame(shot.end(), fps);
            int from = Math.max(range.from(), start);
            int to = Math.min(range.to(), end);
            if (to <= from) {
                continue;
            }
            int shotFrames = end - start;
            int punch = shot.punch() == null ? Integer.MAX_VALUE : start + punchFrame(shot, fps);
            if (punch > from && punch < to) {
                pieces.add(new Piece(shot, from, punch, false, start, shotFrames));
                pieces.add(new Piece(shot, punch, to, true, start, shotFrames));
            } else {
                pieces.add(new Piece(shot, from, to, punch <= from, start, shotFrames));
            }
        }
        return pieces;
    }

    /**
     * The FFmpeg filter that draws a piece exactly like Remotion: the media fitted to the frame, its slow push or
     * Ken Burns move (same easing, over the shot's length plus the overlap, like the Sequence in LongForm.jsx), and
     * the punch-in (PunchIn.jsx) with its camera-flash frames.
     */
    public static String filter(Piece piece, int width, int height, int fps) {
        EdlShot shot = piece.shot();
        int offset = piece.from() - piece.shotFrom();
        double duration = piece.shotFrames() + OVERLAP_FRAMES;
        String p = f("min(1,max(0,(%d+n)/%.1f))", offset, duration);
        String s;
        String tx = "0";
        String ty = "0";
        switch (shot.media()) {
            case "clip" -> s = f("(1+0.08*%s)", easeQuad(p));
            case "footage" -> s = f("(1.02+0.08*%s)", p);
            case "host" -> s = f("(1+0.05*%s)", p);
            default -> {
                double[] m = MOTIONS.getOrDefault(shot.motion() == null ? "push_in" : shot.motion(),
                        MOTIONS.get("push_in"));
                String e = easeCubic(p);
                s = f("(%.4f+%.4f*%s)", m[0], m[1] - m[0], e);
                tx = f("((%.4f+%.4f*%s)/100*%d)", m[2], m[3] - m[2], e, width);
                ty = f("((%.4f+%.4f*%s)/100*%d)", m[4], m[5] - m[4], e, height);
            }
        }
        String pz = "1";
        double ox = width / 2.0;
        double oy = height / 2.0;
        String brightness = "0";
        if (piece.punched() && shot.punch() != null) {
            int punchFrame = punchFrame(shot, fps);
            pz = f("(%.4f*(1+0.05*min(1,max(0,(%d+n-%d)/120))))", shot.punch().zoom(), offset, punchFrame);
            ox = shot.punch().x() * width;
            oy = shot.punch().y() * height;
            int flashStart = punchFrame - offset;
            if (flashStart >= 0) {
                // PunchIn's white flash: 30% on the punch frame, 12% on the next two.
                brightness = f("if(eq(n,%d),0.22,if(between(n,%d,%d),0.09,0))", flashStart, flashStart + 1,
                        flashStart + 2);
            }
        }
        String k = "(" + s + "*" + pz + ")";
        // Source frame scaled by K, then cropped where output (0,0) falls: pz*cx*(s-1) + O*(pz-1) - pz*T.
        String x = f("min(max(0,%s*%.1f*(%s-1)+%.1f*(%s-1)-%s*%s),iw-%d)", pz, width / 2.0, s, ox, pz, pz, tx,
                width);
        String y = f("min(max(0,%s*%.1f*(%s-1)+%.1f*(%s-1)-%s*%s),ih-%d)", pz, height / 2.0, s, oy, pz, pz, ty,
                height);
        double[] grade = GRADES.getOrDefault(shot.media(), GRADES.get("ai"));
        String eq = f(",eq=contrast=%.3f:saturation=%.3f:brightness='%.3f+%s':eval=%s", grade[0], grade[1],
                grade[2], brightness, "0".equals(brightness) ? "init" : "frame");
        return f("fps=%d,scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d,setsar=1,"
                        + "scale=w='trunc(%d*%s/2)*2':h='trunc(%d*%s/2)*2':eval=frame:flags=bicubic,"
                        + "crop=%d:%d:x='%s':y='%s'%s",
                fps, width, height, width, height, width, k, height, k, width, height, x, y, eq);
    }

    /** The punch frame inside the shot, rounded like PunchIn.jsx. */
    static int punchFrame(EdlShot shot, int fps) {
        return (int) Math.round((shot.punch().at() - shot.start()) * fps);
    }

    static String easeQuad(String p) {
        return f("if(lt(%s,0.5),2*pow(%s,2),1-pow(-2*%s+2,2)/2)", p, p, p);
    }

    static String easeCubic(String p) {
        return f("if(lt(%s,0.5),4*pow(%s,3),1-pow(-2*%s+2,3)/2)", p, p, p);
    }

    static int frame(double seconds, int fps) {
        return (int) Math.round(seconds * fps);
    }

    private static String f(String format, Object... values) {
        return String.format(Locale.ROOT, format, values);
    }
}
