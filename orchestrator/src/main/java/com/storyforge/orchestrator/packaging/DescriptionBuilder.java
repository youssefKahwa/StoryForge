package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import java.util.List;

public final class DescriptionBuilder {

    public static final String AI_DISCLOSURE =
            "Narration and illustrations in this video were produced with AI tools; research and sources are listed above.";

    private DescriptionBuilder() {
    }

    public static final int MAX_CHARS = 5000;
    private static final int MIN_CHAPTERS = 3;
    private static final double MIN_CHAPTER_SECONDS = 10;

    public static String build(String hookLine, List<EdlChapter> chapters, List<Dossier.Source> sources,
                               List<String> musicCredits) {
        return build(hookLine, chapters, sources, musicCredits, List.of());
    }

    public static String build(String hookLine, List<EdlChapter> chapters, List<Dossier.Source> sources,
                               List<String> musicCredits, List<String> hashtags) {
        return build(hookLine, chapters, sources, musicCredits, List.of(), hashtags);
    }

    /** With real photo/footage credits; if they do not fit YouTube's limit, a short "author (license)" form is used. */
    public static String build(String hookLine, List<EdlChapter> chapters, List<Dossier.Source> sources,
                               List<String> musicCredits, List<String> mediaCredits, List<String> hashtags) {
        String full = compose(hookLine, chapters, sources, musicCredits, mediaCredits, hashtags);
        if (full.length() <= MAX_CHARS || mediaCredits.isEmpty()) {
            return full.length() <= MAX_CHARS ? full : full.substring(0, MAX_CHARS);
        }
        List<String> compact = mediaCredits.stream().map(DescriptionBuilder::compactCredit).distinct().toList();
        String shorter = compose(hookLine, chapters, sources, musicCredits, compact, hashtags);
        return shorter.length() <= MAX_CHARS ? shorter : shorter.substring(0, MAX_CHARS);
    }

    public static String compactCredit(String credit) {
        return credit.replaceAll(",? ?(via Wikimedia Commons|on Pexels)?:? https?://\\S+$", "")
                .replaceAll("^\"[^\"]*\" by ", "").strip();
    }

    private static String compose(String hookLine, List<EdlChapter> chapters, List<Dossier.Source> sources,
                                  List<String> musicCredits, List<String> mediaCredits, List<String> hashtags) {
        StringBuilder out = new StringBuilder();
        out.append(hookLine.strip()).append("\n\n");
        List<EdlChapter> valid = youtubeChapters(chapters);
        if (!valid.isEmpty()) {
            out.append("Chapters\n");
            valid.forEach(chapter -> out.append(timestamp(chapter.start())).append(' ')
                    .append(chapter.title()).append('\n'));
            out.append('\n');
        }
        if (!sources.isEmpty()) {
            out.append("Sources\n");
            sources.forEach(source -> out.append("- ").append(cleanTitle(source.title()))
                    .append(source.publisher() == null ? "" : " (" + source.publisher() + ")")
                    .append(": ").append(source.url()).append('\n'));
            out.append('\n');
        }
        if (!musicCredits.isEmpty()) {
            out.append("Music\n");
            musicCredits.forEach(credit -> out.append("- ").append(credit).append('\n'));
            out.append('\n');
        }
        if (!mediaCredits.isEmpty()) {
            out.append("Photos and footage\n");
            mediaCredits.forEach(credit -> out.append("- ").append(credit).append('\n'));
            out.append('\n');
        }
        out.append(AI_DISCLOSURE).append('\n');
        if (!hashtags.isEmpty()) {
            out.append('\n').append(String.join(" ", hashtags.stream().limit(3).map(DescriptionBuilder::hashtag)
                    .toList())).append('\n');
        }
        return out.toString();
    }

    private static final java.util.regex.Pattern NOTE = java.util.regex.Pattern.compile(
            "\\s*\\((?=[^()]*\\b(?:(?-i:I)|despite|didn't|did not|load|loaded|search results|unverified|URL)\\b)[^()]*\\)",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final int MAX_TITLE = 110;

    static String cleanTitle(String title) {
        if (title == null) {
            return "Source";
        }
        String clean = NOTE.matcher(title).replaceAll("");
        int sentence = clean.indexOf(". ");
        if (sentence > 20) {
            clean = clean.substring(0, sentence);
        }
        clean = clean.strip();
        return clean.length() <= MAX_TITLE ? clean : clean.substring(0, MAX_TITLE - 1).strip() + "\u2026";
    }

    public static List<EdlChapter> youtubeChapters(List<EdlChapter> chapters) {
        java.util.ArrayList<EdlChapter> valid = new java.util.ArrayList<>();
        for (EdlChapter chapter : chapters) {
            double start = valid.isEmpty() ? 0 : chapter.start();
            if (!valid.isEmpty() && start - valid.get(valid.size() - 1).start() < MIN_CHAPTER_SECONDS) {
                continue;
            }
            valid.add(new EdlChapter(start, chapter.title()));
        }
        return valid.size() >= MIN_CHAPTERS ? valid : List.of();
    }

    private static String hashtag(String tag) {
        String clean = tag.replaceAll("[^\\p{L}\\p{N}]", "");
        return "#" + clean;
    }

    public static String timestamp(double seconds) {
        long total = (long) Math.floor(seconds);
        long hours = total / 3600;
        long minutes = total / 60 % 60;
        long secs = total % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, secs) : "%d:%02d".formatted(minutes, secs);
    }
}
