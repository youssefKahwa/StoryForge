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

    public MediaSvcClient(StoryForgeProperties properties) {
        this.url = properties.mediaSvc().url();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(properties.mediaSvc().timeoutSeconds()));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public double tts(List<Scene.Segment> segments, String voice, double speed, String lang, Path out) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Scene.Segment segment : segments) {
            parts.add(segment.silent() ? Map.of("pause_ms", segment.pauseMs()) : Map.of("text", segment.text()));
        }
        JsonNode response = post("/tts", Map.of("segments", parts, "voice", voice, "speed", speed, "lang", lang,
                "out_path", out.toAbsolutePath().toString()));
        return response.path("duration_sec").asDouble(0);
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

    public VideoTranscript transcript(String videoUrl) {
        JsonNode response = post("/transcript", Map.of("url", videoUrl));
        JsonNode duration = response.path("duration_sec");
        return new VideoTranscript(videoUrl, response.path("video_id").asString(null),
                response.path("title").asString(null), response.path("channel").asString(null),
                duration.isNumber() ? duration.asInt() : null, response.path("language").asString(null),
                response.path("source").asString(null), response.path("transcript").asString(""));
    }

    private JsonNode post(String path, Map<String, Object> body) {
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
