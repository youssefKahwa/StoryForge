package com.storyforge.orchestrator.provider.geo;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.MapPin;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Coordinates of a named place from Wikidata (no key, CC0 data): entity search, then the first result with a
 * coordinate location (P625). Results are cached in the library so each place is looked up once.
 * https://www.wikidata.org/w/api.php (wbsearchentities, wbgetentities), checked 2026-10-20.
 */
@Component
public class WikidataGeo {

    private static final Logger log = LoggerFactory.getLogger(WikidataGeo.class);
    private static final String USER_AGENT = "StoryForge/0.2 (personal documentary tool; non-commercial API use)";
    private static final String API = "https://www.wikidata.org/w/api.php?format=json";

    private final JsonMapper json;
    private final Path cacheFile;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final boolean enabled;

    public WikidataGeo(JsonMapper json, StoryForgeProperties properties,
                       @org.springframework.beans.factory.annotation.Value("${storyforge.real-media.enabled:false}")
                       boolean enabled) {
        this.json = json;
        this.enabled = enabled;
        this.cacheFile = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("geo-cache.json");
    }

    public synchronized Optional<MapPin> locate(String place) {
        if (!enabled || place == null || place.isBlank()) {
            return Optional.empty();
        }
        String key = place.strip().toLowerCase(Locale.ROOT);
        Map<String, MapPin> cache = cache();
        if (cache.containsKey(key)) {
            return Optional.ofNullable(cache.get(key));
        }
        MapPin pin = null;
        try {
            pin = lookup(place.strip());
        } catch (IOException | RuntimeException e) {
            log.warn("Wikidata lookup failed for '{}': {}", place, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        cache.put(key, pin);
        save(cache);
        return Optional.ofNullable(pin);
    }

    private MapPin lookup(String place) throws IOException, InterruptedException {
        // "Eilean Mor, Scotland": search the specific part, keep the full text as the label.
        String name = place.split(",")[0].strip();
        JsonNode search = get(API + "&action=wbsearchentities&language=en&limit=5&search=" + enc(name));
        List<String> ids = new ArrayList<>();
        search.path("search").forEach(hit -> ids.add(hit.path("id").asString("")));
        if (ids.isEmpty()) {
            return null;
        }
        JsonNode entities = get(API + "&action=wbgetentities&props=claims&ids=" + enc(String.join("|", ids)))
                .path("entities");
        for (String id : ids) {
            JsonNode value = entities.path(id).path("claims").path("P625").path(0).path("mainsnak")
                    .path("datavalue").path("value");
            if (value.has("latitude") && value.has("longitude")) {
                return new MapPin(place, value.path("latitude").asDouble(), value.path("longitude").asDouble());
            }
        }
        return null;
    }

    private JsonNode get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from Wikidata");
        }
        return json.readTree(response.body());
    }

    private Map<String, MapPin> cache() {
        try {
            if (Files.isRegularFile(cacheFile)) {
                return new LinkedHashMap<>(json.readValue(cacheFile.toFile(), new TypeReference<Map<String, MapPin>>() { }));
            }
        } catch (RuntimeException e) {
            log.warn("Geo cache unreadable, starting fresh: {}", e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    private void save(Map<String, MapPin> cache) {
        try {
            Files.createDirectories(cacheFile.getParent());
            json.writerWithDefaultPrettyPrinter().writeValue(cacheFile.toFile(), cache);
        } catch (IOException | RuntimeException e) {
            log.warn("Geo cache not saved: {}", e.getMessage());
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
