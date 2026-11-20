package com.storyforge.orchestrator.model;

import java.util.List;

public record Edl(
        int version,
        int width,
        int height,
        int fps,
        double durationSec,
        String lut,
        List<EdlShot> shots,
        List<EdlAudio> audio,
        List<EdlCaption> captions,
        List<EdlChapter> chapters,
        EdlStyle style,
        List<Overlay> overlays,
        HostTrack host) {

    public Edl withShots(List<EdlShot> newShots) {
        return new Edl(version, width, height, fps, durationSec, lut, newShots, audio, captions, chapters, style,
                overlays, host);
    }

    public Edl withOverlays(List<Overlay> newOverlays) {
        return new Edl(version, width, height, fps, durationSec, lut, shots, audio, captions, chapters, style,
                newOverlays, host);
    }

    /** Same EDL with the talking host added (or removed with null). */
    public Edl withHost(HostTrack track) {
        return new Edl(version, width, height, fps, durationSec, lut, shots, audio, captions, chapters, style,
                overlays, track);
    }

    /** The channel's talking logo: frames under {@code framePattern} (relative to the run), shown per appearance. */
    /** logo: when set, the bubble draws this logo with a vector mouth (open/shape per frame) instead of frames. */
    public record HostTrack(String framePattern, int frameCount, int size, List<HostAppearance> appearances,
                            String logo) {
    }

    /** One pop-in: start/end in seconds and the frame index (0-based) to show on each video frame. */
    /** open (0..1) and shape (-1 "oh" .. 1 "ee") per video frame drive the vector mouth; frames the old one. */
    public record HostAppearance(double start, double end, List<Integer> frames, List<Double> open,
                                 List<Double> shape) {
    }

    public record EdlStyle(List<String> fonts, List<String> palette, boolean burnCaptions) {
    }

    /**
     * media: "ai" (generated still), "photo" (real photograph), "footage" (real video), "clip" (fair-use trailer) or
     * "map". punch: optional punch-in inside the shot (same picture, tighter crop) to keep the picture moving.
     */
    public record EdlShot(String id, String sceneId, String type, double start, double end, String asset,
                          String motion, String text, String transition, String media, String credit,
                          MapPin map, EdlPunch punch, MotionGraphic animation) {

        public EdlShot(String id, String sceneId, String type, double start, double end, String asset,
                       String motion, String text, String transition, String media, String credit, MapPin map,
                       EdlPunch punch) {
            this(id, sceneId, type, start, end, asset, motion, text, transition, media, credit, map, punch, null);
        }
    }

    /** From {@code at} (seconds, absolute) to the end of the shot the picture is cropped around (x, y), 0..1. */
    public record EdlPunch(double at, double x, double y, double zoom) {
    }

    public record EdlAudio(String kind, String file, double start, double gainDb, LicenseRecord license) {
    }

    public record EdlCaption(double start, double end, String text) {
    }

    public record EdlChapter(double start, String title) {
    }
}
