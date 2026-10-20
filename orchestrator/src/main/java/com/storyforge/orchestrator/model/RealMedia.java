package com.storyforge.orchestrator.model;

/** The director's request for real media on a shot: a "photo" (Wikimedia Commons) or "footage" (stock video). */
public record RealMedia(String kind, String query) {

    public static final String PHOTO = "photo";
    public static final String FOOTAGE = "footage";

    public boolean valid() {
        return (PHOTO.equals(kind) || FOOTAGE.equals(kind)) && query != null && !query.isBlank()
                && query.length() <= 80;
    }
}
