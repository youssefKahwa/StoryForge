package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.GrowthReport;
import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.script.SpokenCheck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the Growth panel from the package and the research, with the rules of youtube-agent-skill (MIT): the
 * opening confirms the title, the keyword sits in the 40 characters the mobile feed shows, thumbnail text of at most 3
 * words, 3 target searches in the title and the first 2 description lines, chapters as 3-5 word promises, at most 15
 * tags, competitors judged by views against their own channel's median, and one fix first.
 */
public final class GrowthAdvisor {

    public static final int MAX_TAGS = 15;
    static final int TARGETS = 3;
    static final int MAX_THUMBNAIL_WORDS = 3;

    /** Everything the panel looks at. introAt: where the channel intro starts (null when there is none). */
    public record Input(GrowthResearch.Research research, List<String> titles, String topic, String opening,
                        List<String> tags, String description, String thumbnailText, List<Edl.EdlChapter> chapters,
                        double durationSec, String lastNarration, Double introAt, int shorts) {
    }

    private GrowthAdvisor() {
    }

    public static GrowthReport build(Input in) {
        GrowthResearch.Research research = in.research();
        List<ScoredTitle> ranked = TitleScorer.top(in.titles() == null ? List.of() : in.titles(), in.topic(), 20,
                research.searches(), in.opening());
        List<String> tags = in.tags() == null ? List.of() : in.tags().stream().limit(MAX_TAGS).toList();
        String top = in.description().lines().limit(2).collect(Collectors.joining("\n"));
        String best = ranked.isEmpty() ? "" : ranked.get(0).title();
        String bestLower = best.toLowerCase(Locale.ROOT);
        String topLower = top.toLowerCase(Locale.ROOT);
        String used = (bestLower + " " + String.join(" ", tags) + " " + topLower).toLowerCase(Locale.ROOT);

        List<GrowthReport.Search> searches = research.searches().stream()
                .map(phrase -> new GrowthReport.Search(phrase, used.contains(phrase))).toList();
        List<GrowthReport.Target> targets = targets(research.searches(), research.name()).stream()
                .map(p -> new GrowthReport.Target(p, bestLower.contains(p), topLower.contains(p))).toList();
        List<GrowthReport.Competitor> competitors = competitors(research.competitors(), research.medians());

        List<GrowthReport.Check> checks = new ArrayList<>();
        boolean introFirst = in.introAt() != null && in.introAt() < 5;
        checks.add(new GrowthReport.Check("The story starts before the channel intro", !introFirst,
                introFirst ? "the intro plays first: viewers leave in the first seconds" : "the hook comes first"));
        boolean confirms = TitleScorer.openingConfirms(best, in.opening());
        checks.add(new GrowthReport.Check("The opening confirms the title", confirms,
                confirms ? "the first sentences say what the title promises"
                        : "the first sentence should say what the title promises, or viewers feel baited and leave"));
        String phrase = TitleScorer.searchedPhrase(best, research.searches());
        checks.add(new GrowthReport.Check("The title uses a phrase people search", phrase != null,
                phrase != null ? "\"" + phrase + "\"" : "none of the searched phrases is in the best title"));
        checks.add(new GrowthReport.Check("The title fits 60 characters", best.length() <= TitleScorer.MAX_LENGTH,
                best.length() + " characters"));
        String name = research.name() == null ? "" : research.name().strip().toLowerCase(Locale.ROOT);
        int at = name.isEmpty() ? -1 : TitleScorer.keywordAt(best, java.util.Set.of(name));
        checks.add(new GrowthReport.Check("The name is in the first 40 characters (mobile feed)",
                at >= 0 && at < TitleScorer.MOBILE_LENGTH - 8,
                at < 0 ? "the title does not name it" : "at character " + (at + 1)));
        String thumb = in.thumbnailText() == null ? "" : in.thumbnailText().strip();
        boolean repeats = !thumb.isEmpty() && bestLower.contains(thumb.toLowerCase(Locale.ROOT));
        int thumbWords = thumb.isEmpty() ? 0 : thumb.split("\\s+").length;
        checks.add(new GrowthReport.Check("The thumbnail text adds to the title in 3 words or less",
                !repeats && thumbWords <= MAX_THUMBNAIL_WORDS,
                "\"" + thumb + "\"" + (repeats ? " repeats the title" : "") + (thumbWords > MAX_THUMBNAIL_WORDS
                        ? " has " + thumbWords + " words" : "")));
        long targetsHit = targets.stream().filter(t -> t.inTitle() || t.inDescription()).count();
        checks.add(new GrowthReport.Check("The target searches are in the title or the first 2 lines",
                !targets.isEmpty() && targetsHit == targets.size(),
                targetsHit + " of " + targets.size() + " (" + targets.stream().map(GrowthReport.Target::phrase)
                        .collect(Collectors.joining(", ")) + ")"));
        List<Edl.EdlChapter> shown = DescriptionBuilder.youtubeChapters(in.chapters() == null ? List.of() : in.chapters());
        checks.add(new GrowthReport.Check("3+ chapters (YouTube shows them)", shown.size() >= 3,
                shown.size() + " chapter(s)"));
        long promises = shown.stream().filter(c -> words(c.title()) >= 3 && words(c.title()) <= 5).count();
        checks.add(new GrowthReport.Check("Chapter titles are 3-5 word promises",
                !shown.isEmpty() && promises == shown.size(), promises + " of " + shown.size()));
        double minutes = in.durationSec() / 60;
        checks.add(new GrowthReport.Check("8+ minutes (mid-roll ads)", minutes >= 8,
                String.format(Locale.ROOT, "%.1f minutes", minutes)));
        long asks = in.lastNarration() == null ? 0 : SpokenCheck.askKinds(in.lastNarration());
        checks.add(new GrowthReport.Check("One ask at the end", asks <= 1, asks + " kind(s) of ask in the last scene"));
        int allTags = in.tags() == null ? 0 : in.tags().size();
        checks.add(new GrowthReport.Check("At most 15 tags", allTags <= MAX_TAGS,
                allTags + " tag(s)" + (allTags > MAX_TAGS ? ", the extra ones were dropped" : "")));

        String oneFix = checks.stream().filter(c -> !c.ok()).findFirst()
                .map(c -> c.item() + ": " + c.detail())
                .orElse("Nothing to fix: the package follows every rule. Watch the first 30 seconds' retention.");
        return new GrowthReport(research.name(), ranked, searches, competitors, tags, top, checks, targets, oneFix,
                plan(in.shorts()));
    }

    /** The 3 searches to be found for: the most typed phrases that name the subject, then the most typed others. */
    static List<String> targets(List<String> searches, String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        List<String> multi = searches.stream().filter(s -> s.split("\\s+").length >= 2).toList();
        List<String> out = new ArrayList<>(multi.stream().filter(s -> !n.isBlank() && s.contains(n) && !s.equals(n))
                .limit(TARGETS).toList());
        multi.stream().filter(s -> !out.contains(s)).limit(TARGETS - out.size()).forEach(out::add);
        return out;
    }

    /** Biggest outliers first (they show what packaging works), then the most viewed without a known median. */
    static List<GrowthReport.Competitor> competitors(List<MediaSvcClient.SearchHit> hits, Map<String, Long> medians) {
        return hits.stream().map(hit -> {
            long median = medians == null ? 0 : medians.getOrDefault(hit.videoId(), 0L);
            double outlier = median > 0 ? Math.round(hit.views() * 10.0 / median) / 10.0 : 0;
            return new GrowthReport.Competitor(hit.title(), hit.channel(), hit.views(), hit.durationSec(), hit.url(),
                    median, outlier);
        }).sorted(Comparator.comparingDouble(GrowthReport.Competitor::outlier).reversed()
                .thenComparing(Comparator.comparingLong(GrowthReport.Competitor::views).reversed())).toList();
    }

    /** One week: the long video, then its Shorts spread out, a community post between them. */
    static List<GrowthReport.Step> plan(int shorts) {
        List<GrowthReport.Step> steps = new ArrayList<>();
        steps.add(new GrowthReport.Step(1, "Publish the video when your viewers are online (YouTube Studio > "
                + "Analytics > Audience) and pin the comment question"));
        int[] days = {2, 4, 6};
        for (int i = 0; i < Math.min(shorts, days.length); i++) {
            steps.add(new GrowthReport.Step(days[i], "Short " + (i + 1) + ", with a link to the full video"));
        }
        steps.add(new GrowthReport.Step(3, "Community post: the teaser question"));
        steps.sort(Comparator.comparingInt(GrowthReport.Step::day));
        return steps;
    }

    private static int words(String text) {
        return text == null || text.isBlank() ? 0 : text.strip().split("\\s+").length;
    }
}
