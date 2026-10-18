package com.storyforge.orchestrator.packaging;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TagFitter {

    public static final int MAX_TOTAL_CHARS = 500;
    private static final int MAX_TAG_CHARS = 100;

    private TagFitter() {
    }

    public static List<String> fit(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        int total = 0;
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.replace("#", "").replace(",", " ").replaceAll("[<>]", "")
                    .replaceAll("\\s+", " ").strip();
            if (tag.isEmpty() || tag.length() > MAX_TAG_CHARS || !seen.add(tag.toLowerCase(Locale.ROOT))) {
                continue;
            }
            int cost = tag.length() + (tag.contains(" ") ? 2 : 0) + (out.isEmpty() ? 0 : 1);
            if (total + cost > MAX_TOTAL_CHARS) {
                break;
            }
            out.add(tag);
            total += cost;
        }
        return out;
    }
}
