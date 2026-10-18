package com.storyforge.orchestrator.youtube;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class YouTubeAuthService {

    public static final String SCOPES = "https://www.googleapis.com/auth/youtube.upload "
            + "https://www.googleapis.com/auth/youtube.force-ssl";
    private static final String AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String verifier, Instant createdAt) {
    }

    private record AccessToken(String value, Instant expiresAt) {
    }

    private final StoryForgeProperties.YouTube config;
    private final Path tokenFile;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile AccessToken accessToken;

    public YouTubeAuthService(StoryForgeProperties properties, JsonMapper json) {
        this.config = properties.youtube();
        this.tokenFile = Path.of(properties.dataDir()).toAbsolutePath().resolve("secrets").resolve("youtube.json");
        this.json = json;
    }

    public boolean configured() {
        return notBlank(config.clientId()) && notBlank(config.clientSecret());
    }

    public boolean connected() {
        return Files.isRegularFile(tokenFile);
    }

    public String redirectUri() {
        return config.redirectUri();
    }

    public String authorizationUrl() {
        if (!configured()) {
            throw new IllegalStateException("Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET in .env first");
        }
        String state = token(24);
        String verifier = token(48);
        pending.put(state, new Pending(verifier, Instant.now()));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", config.clientId());
        params.put("redirect_uri", config.redirectUri());
        params.put("response_type", "code");
        params.put("scope", SCOPES);
        params.put("code_challenge", challenge(verifier));
        params.put("code_challenge_method", "S256");
        params.put("state", state);
        params.put("access_type", "offline");
        params.put("prompt", "consent");
        return AUTH_URL + "?" + form(params);
    }

    public void completeAuthorization(String state, String code) {
        Pending request = pending.remove(state);
        if (request == null || request.createdAt().isBefore(Instant.now().minus(Duration.ofMinutes(15)))) {
            throw new IllegalArgumentException("Unknown or expired authorization state; start again");
        }
        JsonNode response = postToken(Map.of(
                "client_id", config.clientId(),
                "client_secret", config.clientSecret(),
                "code", code,
                "code_verifier", request.verifier(),
                "grant_type", "authorization_code",
                "redirect_uri", config.redirectUri()));
        String refresh = response.path("refresh_token").asString("");
        if (refresh.isEmpty()) {
            throw new IllegalStateException("Google returned no refresh token");
        }
        try {
            Files.createDirectories(tokenFile.getParent());
            Files.writeString(tokenFile, json.writeValueAsString(Map.of("refresh_token", refresh,
                    "scope", response.path("scope").asString(""), "created_at", Instant.now().toString())),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        cache(response);
    }

    public void disconnect() {
        try {
            Files.deleteIfExists(tokenFile);
            accessToken = null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized String accessToken() {
        AccessToken current = accessToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.value();
        }
        if (!connected()) {
            throw new IllegalStateException("YouTube is not connected; use Settings > Connect YouTube");
        }
        String refresh = json.readValue(tokenFile, JsonNode.class).path("refresh_token").asString("");
        JsonNode response = postToken(Map.of(
                "client_id", config.clientId(),
                "client_secret", config.clientSecret(),
                "refresh_token", refresh,
                "grant_type", "refresh_token"));
        return cache(response);
    }

    private String cache(JsonNode response) {
        String value = response.path("access_token").asString("");
        long seconds = response.path("expires_in").asLong(3600);
        accessToken = new AccessToken(value, Instant.now().plusSeconds(seconds));
        return value;
    }

    private JsonNode postToken(Map<String, String> fields) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Google token endpoint returned " + response.statusCode() + ": "
                        + response.body());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted calling Google", e);
        }
    }

    private static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String token(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
