package com.storyforge.orchestrator.model;

/** Documentary graphic drawn over the picture: a location/date lower third, a quotation card or a year marker. */
public record Overlay(String kind, String shotId, double start, double end, String title, String subtitle) {

    public static final String LOCATION = "location";
    public static final String QUOTE = "quote";
    public static final String YEAR = "year";
}
