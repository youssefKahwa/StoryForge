package com.storyforge.orchestrator.qa;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class OriginalityChecker {

    public static final int NGRAM = 6;

    public record Result(int longestSharedRun, double sharedNgramShare, String example) {

        public boolean original(int maxRun, double maxShare) {
            return longestSharedRun < maxRun && sharedNgramShare <= maxShare;
        }
    }

    private OriginalityChecker() {
    }

    public static Result compare(String script, List<String> sources) {
        List<String> ours = tokens(script);
        int longest = 0;
        String example = null;
        Set<String> theirGrams = new HashSet<>();
        for (String source : sources) {
            List<String> theirs = tokens(source);
            theirGrams.addAll(ngrams(theirs));
            int[] run = longestCommonRun(ours, theirs);
            if (run[0] > longest) {
                longest = run[0];
                example = String.join(" ", ours.subList(run[1] - run[0] + 1, run[1] + 1));
            }
        }
        List<String> ourGrams = ngrams(ours);
        long shared = ourGrams.stream().filter(theirGrams::contains).count();
        double share = ourGrams.isEmpty() ? 0 : (double) shared / ourGrams.size();
        return new Result(longest, Math.round(share * 10_000) / 10_000.0, example);
    }

    static List<String> tokens(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}']+"))
                .filter(token -> !token.isEmpty()).toList();
    }

    private static List<String> ngrams(List<String> tokens) {
        List<String> grams = new ArrayList<>();
        for (int i = 0; i + NGRAM <= tokens.size(); i++) {
            grams.add(String.join(" ", tokens.subList(i, i + NGRAM)));
        }
        return grams;
    }

    private static int[] longestCommonRun(List<String> a, List<String> b) {
        Map<String, List<Integer>> positions = new HashMap<>();
        for (int j = 0; j < b.size(); j++) {
            positions.computeIfAbsent(b.get(j), key -> new ArrayList<>()).add(j);
        }
        int[] previous = new int[b.size() + 1];
        int best = 0;
        int bestEnd = -1;
        for (int i = 0; i < a.size(); i++) {
            int[] current = new int[b.size() + 1];
            for (int j : positions.getOrDefault(a.get(i), List.of())) {
                current[j + 1] = previous[j] + 1;
                if (current[j + 1] > best) {
                    best = current[j + 1];
                    bestEnd = i;
                }
            }
            previous = current;
        }
        return new int[]{best, bestEnd};
    }
}
