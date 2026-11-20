package com.storyforge.orchestrator.provider.media;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.ClipAlignment.ClipWord;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.VideoTranscript;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component
public class MediaSvcClient {

    private final RestClient client;
    private final String url;
    private final MediaSvcLauncher launcher;

    public MediaSvcClient(StoryForgeProperties properties, MediaSvcLauncher launcher) {
        this.launcher = launcher;
        this.url = properties.mediaSvc().url();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.mediaSvc().timeoutSeconds()));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public double tts(List<Scene.Segment> segments, String voice, double speed, String lang, Path out) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Scene.Segment segment : segments) {
            if (segment.silent()) {
                parts.add(Map.of("pause_ms", segment.pauseMs() > 0 ? segment.pauseMs() : 600));
            } else if (segment.spoken()) {
                parts.add(Map.of("text", segment.text()));
            }
        }
        JsonNode response = post("/tts", Map.of("segments", parts, "voice", voice, "speed", speed, "lang", lang,
                "out_path", out.toAbsolutePath().toString()));
        return response.path("duration_sec").asDouble(0);
    }

    /** What the human delivery engine did for one scene. */
    public record Speech(double durationSec, String engine, int chunks, int takes, List<String> issues) {
    }

    /**
     * A scene voiced by the human delivery engine (media-svc /speak): items are the scene's text, mood and beat
     * marks in order; lexicon maps a word to {"ipa": ...} or {"say": ...}.
     */
    public Speech speak(List<Map<String, Object>> items, String engine, String voice, double speed, String lang,
                        String mood, double pace, boolean breaths, Map<String, Map<String, String>> lexicon,
                        String seed, Path out) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("items", items);
        body.put("engine", engine);
        body.put("voice", voice);
        body.put("speed", speed);
        body.put("lang", lang);
        body.put("mood", mood);
        body.put("pace", pace);
        body.put("breaths", breaths);
        body.put("lexicon", lexicon);
        body.put("seed", seed);
        body.put("out_path", out.toAbsolutePath().toString());
        JsonNode response = post("/speak", body);
        List<String> issues = new ArrayList<>();
        response.path("issues").forEach(issue -> issues.add(issue.asString("")));
        return new Speech(response.path("duration_sec").asDouble(0), response.path("engine").asString(engine),
                response.path("chunks").asInt(0), response.path("takes").asInt(0), issues);
    }

    /** Can the Magpie voice be used right now (key, client library, NVIDIA API)? */
    public boolean magpieReady() {
        try {
            if (!launcher.healthy()) {
                launcher.ensureRunning();
            }
            JsonNode response = client.get().uri("/magpie/check").retrieve().body(JsonNode.class);
            return response != null && response.path("ok").asBoolean(false);
        } catch (RestClientException e) {
            return false;
        }
    }

    public ClipAlignment align(Path wav, String text) {
        JsonNode response = post("/align", Map.of("wav_path", wav.toAbsolutePath().toString(), "text", text));
        List<ClipWord> words = new ArrayList<>();
        for (JsonNode word : response.path("words")) {
            words.add(new ClipWord(word.path("text").asString(""), word.path("start").asDouble(0),
                    word.path("end").asDouble(0)));
        }
        return new ClipAlignment(words, response.path("transcript").asString(""), response.path("wer").asDouble(1),
                response.path("duration_sec").asDouble(0));
    }

    /** Official trailer (picture only) for a film or series, downloaded into {@code outDir}. */
    public record Trailer(Path file, String videoId, String title, String channel, String url, double durationSec) {
    }

    /** {@code work}: the exact title that must appear in the trailer's title (no other films, no festival reels). */
    public Trailer trailer(String query, String work, Path outDir) {
        JsonNode r = post("/trailer", Map.of("query", query, "work", work,
                "out_dir", outDir.toAbsolutePath().toString()));
        return new Trailer(Path.of(r.path("file").asString("")), r.path("video_id").asString(""),
                r.path("title").asString(""), r.path("channel").asString(""), r.path("url").asString(""),
                r.path("duration_sec").asDouble(0));
    }

    /**
     * Up to {@code maxVideos} real videos of a screen work (trailers, teasers, TV spots, official clips, scene
     * uploads) for one search, picture only. {@code skipIds}: videos already in the library.
     */
    public List<Trailer> footage(String query, String work, Path outDir, int maxVideos, List<String> skipIds) {
        JsonNode r = post("/footage", Map.of("query", query, "work", work, "max_videos", maxVideos,
                "skip_ids", skipIds, "out_dir", outDir.toAbsolutePath().toString()));
        List<Trailer> videos = new ArrayList<>();
        for (JsonNode v : r.path("videos")) {
            videos.add(new Trailer(Path.of(v.path("file").asString("")), v.path("video_id").asString(""),
                    v.path("title").asString(""), v.path("channel").asString(""), v.path("url").asString(""),
                    v.path("duration_sec").asDouble(0)));
        }
        return videos;
    }

    public double sfx(String name, Path out) {
        return post("/sfx", Map.of("name", name, "out_path", out.toAbsolutePath().toString()))
                .path("duration_sec").asDouble(0);
    }

    public VideoTranscript transcript(String videoUrl) {
        JsonNode response = post("/transcript", Map.of("url", videoUrl));
        JsonNode duration = response.path("duration_sec");
        List<VideoTranscript.Line> segments = new ArrayList<>();
        for (JsonNode line : response.path("segments")) {
            segments.add(new VideoTranscript.Line(line.path("start").asDouble(0), line.path("end").asDouble(0),
                    line.path("text").asString("")));
        }
        List<VideoTranscript.Heat> heatmap = new ArrayList<>();
        for (JsonNode heat : response.path("heatmap")) {
            heatmap.add(new VideoTranscript.Heat(heat.path("start").asDouble(0), heat.path("end").asDouble(0),
                    heat.path("value").asDouble(0)));
        }
        return new VideoTranscript(videoUrl, response.path("video_id").asString(null),
                response.path("title").asString(null), response.path("channel").asString(null),
                duration.isNumber() ? duration.asInt() : null, response.path("language").asString(null),
                response.path("source").asString(null), response.path("transcript").asString(""),
                segments.isEmpty() ? null : segments, heatmap.isEmpty() ? null : heatmap);
    }

    /** One YouTube search result: what already gets views on the topic. */
    public record SearchHit(String videoId, String title, String channel, long views, double durationSec,
                            String url, String channelUrl) {

        public SearchHit(String videoId, String title, String channel, long views, double durationSec, String url) {
            this(videoId, title, channel, views, durationSec, url, null);
        }
    }

    /** The top results of a YouTube search with their view counts (no download). */
    public List<SearchHit> search(String query, int max) {
        JsonNode r = post("/search", Map.of("query", query, "max_results", max));
        List<SearchHit> hits = new ArrayList<>();
        for (JsonNode v : r.path("results")) {
            hits.add(new SearchHit(v.path("video_id").asString(""), v.path("title").asString(""),
                    v.path("channel").asString(""), v.path("views").asLong(0), v.path("duration_sec").asDouble(0),
                    v.path("url").asString(""), v.path("channel_url").asString(null)));
        }
        return hits;
    }

    /** The channel's usual views: the median over its latest uploads (0 when it cannot be read). */
    public long channelMedianViews(String channelUrl) {
        return post("/channel", Map.of("channel_url", channelUrl, "max_videos", 30)).path("median_views").asLong(0);
    }

    /** The picture only of one YouTube video, at most {@code maxHeight} lines, for analysis (never shown). */
    public Trailer video(String videoUrl, Path outDir, int maxHeight) {
        JsonNode r = post("/video", Map.of("url", videoUrl, "out_dir", outDir.toAbsolutePath().toString(),
                "max_height", maxHeight));
        return new Trailer(Path.of(r.path("file").asString("")), r.path("video_id").asString(""),
                r.path("title").asString(""), "", videoUrl, r.path("duration_sec").asDouble(0));
    }

    private JsonNode post(String path, Map<String, Object> body) {
        if (!launcher.healthy()) {
            launcher.ensureRunning();
        }
        try {
            JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
            if (response == null) {
                throw new MediaException("Empty response from media-svc " + path);
            }
            return response;
        } catch (RestClientException e) {
            throw new MediaException("media-svc " + path + " failed (is it running on " + url
                    + "? start it with scripts/media-svc.ps1): " + e.getMessage(), e);
        }
    }
}
