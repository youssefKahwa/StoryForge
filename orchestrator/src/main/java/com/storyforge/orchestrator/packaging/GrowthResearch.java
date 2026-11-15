package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What people actually search on YouTube about the video's subject, and which videos on it already get views. Free and
 * keyless: the suggestions YouTube's own search box shows (US English), and a YouTube search through media-svc. The
 * packaging uses both so titles, tags and the first description lines speak the words viewers type.
 */
@Component
public class GrowthResearch {

    public record Research(String name, List<String> searches, List<MediaSvcClient.SearchHit> competitors) {
        static Research empty(String name) {
            return new Research(name, List.of(), List.of());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(GrowthResearch.class);
    private static final String SUGGEST = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt"
            + "&hl=en&gl=US&q=";
    private static final String USER_AGENT = "StoryForge/0.2 (personal video tool)";
    /** Suggestions that are another language's version, a pirate copy or not about the story. */
    private static final Pattern JUNK = Pattern.compile("\\b(?:hindi|telugu|tamil|bangla|bengali|malayalam|kannada"
            + "|urdu|arabic|marathi|punjabi|indonesia|full movie|download|torrent|free|subtitles?|dubbed|mexico"
            + "|soundtrack|ringtone)\\b", Pattern.CASE_INSENSITIVE);
    private static final int MAX_SEARCHES = 15;
    private static final int COMPETITORS = 10;

    private final MediaSvcClient media;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public GrowthResearch(MediaSvcClient media, JsonMapper json) {
        this.media = media;
        this.json = json;
    }

    /** The short name people search: the inspiration's topic, else the brief's title before any "(" or ":". */
    public static String name(InspirationIdeas ideas, String briefTitle) {
        if (ideas != null && ideas.topic() != null && !ideas.topic().isBlank() && ideas.topic().length() <= 40) {
            return ideas.topic().strip();
        }
        String title = briefTitle == null ? "" : briefTitle;
        int cut = title.length();
        for (String mark : List.of("(", ":", " - ", ",")) {
            int at = title.indexOf(mark);
            if (at > 2) {
                cut = Math.min(cut, at);
            }
        }
        return title.substring(0, cut).strip();
    }

    public Research research(String name) {
        if (name == null || name.isBlank()) {
            return Research.empty(name);
        }
        Set<String> searches = new LinkedHashSet<>();
        for (String seed : List.of(name, name + " explained", name + " ending", name + " analysis")) {
            try {
                suggestions(seed).stream().filter(GrowthResearch::useful).forEach(searches::add);
            } catch (IOException | RuntimeException e) {
                log.warn("Search suggestions for '{}' failed: {}", seed, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Research.empty(name);
            }
        }
        List<String> top = searches.stream().limit(MAX_SEARCHES).toList();
        String query = top.stream().filter(s -> s.contains("explained")).findFirst().orElse(name + " explained");
        List<MediaSvcClient.SearchHit> competitors = new ArrayList<>();
        try {
            competitors.addAll(media.search(query, COMPETITORS));
            competitors.sort(Comparator.comparingLong(MediaSvcClient.SearchHit::views).reversed());
        } catch (RuntimeException e) {
            log.warn("YouTube search for '{}' failed: {}", query, e.getMessage());
        }
        log.info("Growth research for '{}': {} searched phrases, {} competing videos", name, top.size(),
                competitors.size());
        return new Research(name, top, competitors);
    }

    static boolean useful(String phrase) {
        return phrase != null && !phrase.isBlank() && !JUNK.matcher(phrase).find();
    }

    private List<String> suggestions(String seed) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SUGGEST + URLEncoder.encode(seed,
                StandardCharsets.UTF_8))).timeout(Duration.ofSeconds(15)).header("User-Agent", USER_AGENT).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return parse(json.readTree(response.body()));
    }

    /** The search box's answer: ["seed", ["suggestion", ...], ...]. */
    static List<String> parse(JsonNode node) {
        List<String> out = new ArrayList<>();
        node.path(1).forEach(s -> out.add(s.asString("").toLowerCase(Locale.ROOT).strip()));
        return out;
    }
}
