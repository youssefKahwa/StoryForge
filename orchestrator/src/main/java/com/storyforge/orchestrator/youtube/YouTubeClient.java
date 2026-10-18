package com.storyforge.orchestrator.youtube;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class YouTubeClient {

    private static final Logger log = LoggerFactory.getLogger(YouTubeClient.class);
    private static final String API = "https://www.googleapis.com/youtube/v3";
    private static final String UPLOAD = "https://www.googleapis.com/upload/youtube/v3";
    private static final int MAX_ATTEMPTS = 3;

    public record VideoMetadata(String title, String description, List<String> tags, String categoryId,
                                String language, Instant publishAt, boolean syntheticMedia) {
    }

    private final YouTubeAuthService auth;
    private final StoryForgeProperties.YouTube config;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public YouTubeClient(YouTubeAuthService auth, StoryForgeProperties properties, JsonMapper json) {
        this.auth = auth;
        this.config = properties.youtube();
        this.json = json;
    }

    public String channelTitle() {
        JsonNode response = send(HttpRequest.newBuilder(URI.create(API + "/channels?part=snippet&mine=true"))
                .header("Authorization", "Bearer " + auth.accessToken()).GET().build(), 200);
        JsonNode first = response.path("items").path(0);
        return first.isMissingNode() ? null : first.path("snippet").path("title").asString(null);
    }

    public String upload(Path video, VideoMetadata metadata) {
        Map<String, Object> snippet = new LinkedHashMap<>();
        snippet.put("title", metadata.title());
        snippet.put("description", metadata.description());
        snippet.put("tags", metadata.tags());
        snippet.put("categoryId", metadata.categoryId());
        snippet.put("defaultLanguage", metadata.language());
        snippet.put("defaultAudioLanguage", metadata.language());
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("privacyStatus", "private");
        status.put("selfDeclaredMadeForKids", false);
        status.put("containsSyntheticMedia", metadata.syntheticMedia());
        if (metadata.publishAt() != null) {
            status.put("publishAt", metadata.publishAt().toString());
        }
        String body = json.writeValueAsString(Map.of("snippet", snippet, "status", status));
        long size = size(video);
        HttpRequest start = HttpRequest.newBuilder(URI.create(UPLOAD
                        + "/videos?uploadType=resumable&part=snippet,status&notifySubscribers=" + config.notifySubscribers()))
                .header("Authorization", "Bearer " + auth.accessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "video/mp4")
                .header("X-Upload-Content-Length", String.valueOf(size))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        String session = raw(start, 200).headers().firstValue("Location")
                .orElseThrow(() -> new IllegalStateException("YouTube returned no upload session"));
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest put = HttpRequest.newBuilder(URI.create(session))
                        .header("Authorization", "Bearer " + auth.accessToken())
                        .header("Content-Type", "video/mp4")
                        .timeout(Duration.ofHours(2))
                        .PUT(HttpRequest.BodyPublishers.ofFile(video)).build();
                HttpResponse<String> response = http.send(put, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 || response.statusCode() == 201) {
                    return json.readTree(response.body()).path("id").asString();
                }
                log.warn("YouTube upload attempt {} returned {}: {}", attempt, response.statusCode(), response.body());
                if (response.statusCode() < 500) {
                    throw new IllegalStateException("YouTube upload failed " + response.statusCode() + ": "
                            + response.body());
                }
            } catch (IOException e) {
                log.warn("YouTube upload attempt {} failed: {}", attempt, e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted during YouTube upload", e);
            }
        }
        throw new IllegalStateException("YouTube upload failed after " + MAX_ATTEMPTS + " attempts");
    }

    public void setThumbnail(String videoId, Path image, String contentType) {
        send(HttpRequest.newBuilder(URI.create(UPLOAD + "/thumbnails/set?videoId=" + videoId))
                .header("Authorization", "Bearer " + auth.accessToken())
                .header("Content-Type", contentType)
                .POST(fileBody(image)).build(), 200);
    }

    public void insertCaptions(String videoId, Path srt, String language, String name) {
        String boundary = "storyforge-" + UUID.randomUUID();
        String metadata = json.writeValueAsString(Map.of("snippet", Map.of("videoId", videoId, "language", language,
                "name", name, "isDraft", false)));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            body.writeBytes(("--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + metadata
                    + "\r\n--" + boundary + "\r\nContent-Type: application/octet-stream\r\n\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.writeBytes(Files.readAllBytes(srt));
            body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        send(HttpRequest.newBuilder(URI.create(UPLOAD + "/captions?uploadType=multipart&part=snippet"))
                .header("Authorization", "Bearer " + auth.accessToken())
                .header("Content-Type", "multipart/related; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), 200);
    }

    public void keepPrivate(String videoId) {
        String body = json.writeValueAsString(Map.of("id", videoId, "status", Map.of("privacyStatus", "private",
                "selfDeclaredMadeForKids", false)));
        send(HttpRequest.newBuilder(URI.create(API + "/videos?part=status"))
                .header("Authorization", "Bearer " + auth.accessToken())
                .header("Content-Type", "application/json; charset=UTF-8")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), 200);
    }

    private JsonNode send(HttpRequest request, int expected) {
        String body = raw(request, expected).body();
        return body == null || body.isBlank() ? json.readTree("{}") : json.readTree(body);
    }

    private HttpResponse<String> raw(HttpRequest request, int expected) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != expected) {
                throw new IllegalStateException("YouTube API " + request.method() + " " + request.uri().getPath()
                        + " returned " + response.statusCode() + ": " + response.body());
            }
            return response;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling YouTube", e);
        }
    }

    private static HttpRequest.BodyPublisher fileBody(Path file) {
        try {
            return HttpRequest.BodyPublishers.ofFile(existing(file));
        } catch (java.io.FileNotFoundException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path existing(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing file " + file);
        }
        return file;
    }

    private static long size(Path file) {
        try {
            return Files.size(existing(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
