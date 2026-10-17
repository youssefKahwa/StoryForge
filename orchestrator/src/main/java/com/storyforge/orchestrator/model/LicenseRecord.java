package com.storyforge.orchestrator.model;

import java.util.Set;

public record LicenseRecord(String type, String source, String url, String evidence, String jurisdiction) {

    private static final Set<String> FORBIDDEN = Set.of("film_footage", "film_still", "trailer", "official_art",
            "soundtrack", "unknown");

    public static LicenseRecord generated(String provider) {
        return new LicenseRecord("generated", provider, null, "Generated for this video by " + provider, null);
    }

    public boolean complete() {
        return type != null && !type.isBlank() && source != null && !source.isBlank();
    }

    public boolean forbidden() {
        return type == null || FORBIDDEN.contains(type);
    }

    public boolean needsEvidence() {
        return "public_domain".equals(type);
    }
}
