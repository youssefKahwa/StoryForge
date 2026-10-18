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
        EdlStyle style) {

    public record EdlStyle(List<String> fonts, List<String> palette, boolean burnCaptions) {
    }

    public record EdlShot(String id, String sceneId, String type, double start, double end, String asset,
                          String motion, String text, String transition) {
    }

    public record EdlAudio(String kind, String file, double start, double gainDb, LicenseRecord license) {
    }

    public record EdlCaption(double start, double end, String text) {
    }

    public record EdlChapter(double start, String title) {
    }
}
