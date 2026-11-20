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
    /** What the mobile feed shows of a title. */
    public static final int MOBILE_LENGTH = 40;
    static final int MAX_CAPS_WORDS = 2;
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
        return top(titles, topic, limit, searches, null);
    }

    /**
     * opening: the video's first sentences. A title they confirm keeps the viewer who clicked (the click promise is
     * kept at once); one they do not is a bait the viewer leaves.
     */
    public static List<ScoredTitle> top(List<String> titles, String topic, int limit, List<String> searches,
                                        String opening) {
        Set<String> keywords = Arrays.stream(topic.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() > 2 && !STOP.contains(word))
                .collect(Collectors.toSet());
        return titles.stream()
                .filter(title -> title != null && !title.isBlank())
                .map(String::strip)
                .distinct()
                .map(title -> withOpening(withSearches(score(title, keywords), keywords, searches), opening))
                .sorted(Comparator.comparingDouble(ScoredTitle::score).reversed())
                .limit(limit)
                .toList();
    }

    /** True when the opening says at least half of the title's words that carry meaning (two at the least). */
    public static boolean openingConfirms(String title, String opening) {
        if (opening == null || opening.isBlank()) {
            return false;
        }
        Set<String> said = Arrays.stream(opening.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .collect(Collectors.toSet());
        List<String> meaning = Arrays.stream(title.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> word.length() >= 4 && !STOP.contains(word)).distinct().toList();
        long hits = meaning.stream().filter(said::contains).count();
        return hits >= 2 && hits * 2 >= meaning.size();
    }

    private static ScoredTitle withOpening(ScoredTitle scored, String opening) {
        if (!openingConfirms(scored.title(), opening)) {
            return scored;
        }
        List<String> reasons = new ArrayList<>(scored.reasons());
        reasons.add("the opening confirms it");
        return new ScoredTitle(scored.title(), Math.round((scored.score() + 1) * 10) / 10.0, reasons);
    }

    /** Where the topic's first keyword starts in the title, or -1. */
    static int keywordAt(String title, Set<String> keywords) {
        String lower = title.toLowerCase(Locale.ROOT);
        int best = -1;
        for (String keyword : keywords) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(keyword)
                    + "\\b").matcher(lower);
            if (m.find() && (best < 0 || m.start() < best)) {
                best = m.start();
            }
        }
        return best;
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
        // The mobile feed shows about 40 characters: the keyword must be inside them.
        int at = keywordAt(scored.title(), keywords);
        if (at >= 0 && at < MOBILE_LENGTH - 8) {
            score += 0.5;
            reasons.add("keyword in the first " + MOBILE_LENGTH + " chars");
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
        } else if (capsWords(title) > MAX_CAPS_WORDS) {
            score -= 1;
            reasons.add("more than " + MAX_CAPS_WORDS + " all-caps words");
        }
        return new ScoredTitle(title, Math.round(score * 10) / 10.0, reasons);
    }

    /** Words of two letters or more written in capitals ("THIS", "NEVER"). */
    static long capsWords(String title) {
        return Arrays.stream(title.split("[^\\p{L}]+"))
                .filter(word -> word.length() >= 2 && word.equals(word.toUpperCase(Locale.ROOT))
                        && !word.equals(word.toLowerCase(Locale.ROOT)))
                .count();
    }
}
