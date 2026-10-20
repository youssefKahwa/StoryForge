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

    /** Same EDL with the talking host added (or removed with null). */
    public Edl withHost(HostTrack track) {
        return new Edl(version, width, height, fps, durationSec, lut, shots, audio, captions, chapters, style,
                overlays, track);
    }

    /** The channel's talking logo: frames under {@code framePattern} (relative to the run), shown per appearance. */
    public record HostTrack(String framePattern, int frameCount, int size, List<HostAppearance> appearances) {
    }

    /** One pop-in: start/end in seconds and the frame index (0-based) to show on each video frame. */
    public record HostAppearance(double start, double end, List<Integer> frames) {
    }

    public record EdlStyle(List<String> fonts, List<String> palette, boolean burnCaptions) {
    }

    /** media: "ai" (generated still), "photo" (real photograph, shown as a print) or "footage" (real video). */
    public record EdlShot(String id, String sceneId, String type, double start, double end, String asset,
                          String motion, String text, String transition, String media, String credit) {
    }

    public record EdlAudio(String kind, String file, double start, double gainDb, LicenseRecord license) {
    }

    public record EdlCaption(double start, double end, String text) {
    }

    public record EdlChapter(double start, String title) {
    }
}
