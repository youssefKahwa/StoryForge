package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class TitleScorer {

    public static final int MAX_LENGTH = 60;
    private static final Set<String> CURIOSITY = Set.of("why", "how", "what", "secret", "mystery", "never",
            "vanished", "truth", "nobody", "hidden", "real", "lost", "impossible", "strange", "last");
    private static final Set<String> STOP = Set.of("the", "a", "an", "of", "and", "in", "on", "to", "for", "at",
            "is", "was", "who", "that");

    private TitleScorer() {
    }

    public static List<ScoredTitle> top(List<String> titles, String topic, int limit) {
        Set<String> keywords = Arrays.stream(topic.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2 && !STOP.contains(word))
                .collect(Collectors.toSet());
        return titles.stream()
                .filter(title -> title != null && !title.isBlank())
                .map(String::strip)
                .distinct()
                .map(title -> score(title, keywords))
                .sorted(Comparator.comparingDouble(ScoredTitle::score).reversed())
                .limit(limit)
                .toList();
    }

    static ScoredTitle score(String title, Set<String> keywords) {
        double score = 5;
        List<String> reasons = new ArrayList<>();
        if (title.length() <= MAX_LENGTH) {
            score += 2;
            reasons.add("fits " + MAX_LENGTH + " chars");
        } else {
            score -= 4;
            reasons.add("too long (" + title.length() + ")");
        }
        List<String> words = Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty()).toList();
        if (words.stream().anyMatch(keywords::contains)) {
            score += 1.5;
            reasons.add("has keyword");
        }
        long curiosity = words.stream().filter(CURIOSITY::contains).count();
        if (curiosity > 0) {
            score += Math.min(2, curiosity);
            reasons.add("curiosity words");
        }
        if (title.matches(".*\\d.*")) {
            score += 0.5;
            reasons.add("specific number");
        }
        if (words.size() > 12) {
            score -= 1;
            reasons.add("wordy");
        }
        if (title.equals(title.toUpperCase(Locale.ROOT)) && title.length() > 8) {
            score -= 2;
            reasons.add("all caps");
        }
        return new ScoredTitle(title, Math.round(score * 10) / 10.0, reasons);
    }
}
