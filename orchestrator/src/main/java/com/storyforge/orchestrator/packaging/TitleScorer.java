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
        return top(titles, topic, limit, List.of());
    }

    /** searches: phrases people type on YouTube about it; a title that uses one naturally is found more often. */
    public static List<ScoredTitle> top(List<String> titles, String topic, int limit, List<String> searches) {
        Set<String> keywords = Arrays.stream(topic.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2 && !STOP.contains(word))
                .collect(Collectors.toSet());
        return titles.stream()
                .filter(title -> title != null && !title.isBlank())
                .map(String::strip)
                .distinct()
                .map(title -> withSearches(score(title, keywords), keywords, searches))
                .sorted(Comparator.comparingDouble(ScoredTitle::score).reversed())
                .limit(limit)
                .toList();
    }

    /** The searched phrase a title contains, if any (phrases of two words or more). */
    public static String searchedPhrase(String title, List<String> searches) {
        String lower = title.toLowerCase(Locale.ROOT);
        return searches.stream().filter(s -> s.split("\\s+").length >= 2 && lower.contains(s))
                .max(Comparator.comparingInt(String::length)).orElse(null);
    }

    private static ScoredTitle withSearches(ScoredTitle scored, Set<String> keywords, List<String> searches) {
        double score = scored.score();
        List<String> reasons = new ArrayList<>(scored.reasons());
        String phrase = searchedPhrase(scored.title(), searches);
        if (phrase != null) {
            score += 2;
            reasons.add("uses a real search: \"" + phrase + "\"");
        }
        List<String> words = Arrays.stream(scored.title().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty()).toList();
        int first = -1;
        for (int i = 0; i < words.size() && first < 0; i++) {
            if (keywords.contains(words.get(i))) {
                first = i;
            }
        }
        if (first >= 0 && first < Math.max(1, words.size() / 2)) {
            score += 0.5;
            reasons.add("keyword early");
        }
        return new ScoredTitle(scored.title(), Math.round(score * 10) / 10.0, reasons);
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
