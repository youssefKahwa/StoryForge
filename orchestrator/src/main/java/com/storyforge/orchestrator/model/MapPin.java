package com.storyforge.orchestrator.model;

/** A place to show on the animated globe: name as said in the video and its coordinates (Wikidata P625). */
public record MapPin(String label, double lat, double lon) {
}
