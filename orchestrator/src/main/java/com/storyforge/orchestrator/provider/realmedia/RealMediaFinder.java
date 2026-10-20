package com.storyforge.orchestrator.provider.realmedia;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.FfmpegService;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.RealMedia;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Finds real, reusable media for a shot: photographs on Wikimedia Commons (public domain, CC0, CC BY, CC BY-SA only)
 * and stock video on Pexels (Pexels license, needs PEXELS_API_KEY). Every hit carries its license and credit.
 * API references (checked 2026-10-20): https://www.mediawiki.org/wiki/API:Search and
 * https://www.pexels.com/api/documentation/ (video search, Authorization header, 200 requests/hour).
 */
@Component
public class RealMediaFinder {

    /** label: the source's own title and description, used to double-check that the media fits the shot. */
    public record Found(Path file, String media, String provider, LicenseRecord license, String credit, String key,
                        String label) {
    }

    private static final Logger log = LoggerFactory.getLogger(RealMediaFinder.class);
    private static final String USER_AGENT = "StoryForge/0.2 (personal documentary tool; non-commercial API use)";
    private static final Pattern ALLOWED_LICENSE = Pattern.compile(
            "^(public domain|pd(-.*)?|cc0( 1\\.0)?|cc by( \\d\\.\\d)?|cc by-sa( \\d\\.\\d)?)$");
    private static final int MIN_PHOTO_WIDTH = 600;
    private static final double MIN_QUERY_COVERAGE = 0.75;
    /** Photos are shown as prints about 3/4 of the frame high; small Commons files are enlarged to that. */
    private static final int PRINT_HEIGHT = 820;

    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final boolean enabled;
    private final String pexelsKey;
    private final Path cache;

    public RealMediaFinder(FfmpegService ffmpeg, JsonMapper json, StoryForgeProperties properties,
                           @Value("${storyforge.real-media.enabled:false}") boolean enabled,
                           @Value("${storyforge.real-media.pexels-api-key:}") String pexelsKey) {
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.enabled = enabled;
        this.pexelsKey = pexelsKey == null ? "" : pexelsKey.strip();
        this.cache = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("media-cache");
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean footageAvailable() {
        return enabled && !pexelsKey.isEmpty();
    }

    /**
     * Looks for media matching the request. {@code target} has no extension; the found file gets .jpg (photo) or
     * .mp4 (footage, already cut to {@code seconds} at width x height). {@code used} avoids repeating media.
     */
    public Optional<Found> find(RealMedia request, double seconds, int width, int height, Set<String> used,
                                Path target) {
        if (!enabled || request == null || !request.valid()) {
            return Optional.empty();
        }
        try {
            if (RealMedia.FOOTAGE.equals(request.kind()) && !pexelsKey.isEmpty()) {
                Optional<Found> footage = pexels(request.query(), seconds, width, height, used, target);
                if (footage.isPresent()) {
                    return footage;
                }
            }
            return RealMedia.PHOTO.equals(request.kind()) ? commons(request.query(), used, target) : Optional.empty();
        } catch (IOException | RuntimeException e) {
            log.warn("Real media search failed for '{}': {}", request.query(), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private Optional<Found> commons(String query, Set<String> used, Path target)
            throws IOException, InterruptedException {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"
                + "&gsrnamespace=6&gsrlimit=15&gsrsearch=" + enc(query + " filetype:bitmap")
                + "&prop=imageinfo&iiprop=url%7Cmime%7Csize%7Cextmetadata&iiurlwidth=1920"
                + "&iiextmetadatafilter=LicenseShortName%7CArtist%7CRestrictions%7CImageDescription%7CObjectName";
        JsonNode pages = get(url, null).path("query").path("pages");
        List<JsonNode> ordered = new ArrayList<>();
        pages.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(p -> p.path("index").asInt(999)));
        JsonNode best = null;
        double bestScore = -1;
        for (JsonNode page : ordered) {
            JsonNode info = page.path("imageinfo").path(0);
            String mime = info.path("mime").asString("");
            int w = info.path("width").asInt(0);
            int h = info.path("height").asInt(1);
            JsonNode meta = info.path("extmetadata");
            String license = meta.path("LicenseShortName").path("value").asString("").strip();
            String restrictions = meta.path("Restrictions").path("value").asString("").strip();
            String file = info.path("thumburl").asString(info.path("url").asString(""));
            String pageUrl = info.path("descriptionurl").asString("");
            double aspect = (double) w / h;
            if (!(mime.equals("image/jpeg") || mime.equals("image/png")) || w < MIN_PHOTO_WIDTH || aspect < 0.6
                    || aspect > 2.4 || !restrictions.isEmpty() || !allowed(license) || file.isEmpty()
                    || used.contains(pageUrl)) {
                continue;
            }
            double coverage = coverage(query, title(page) + " " + description(meta) + " "
                    + stripHtml(meta.path("ObjectName").path("value").asString("")));
            if (coverage < MIN_QUERY_COVERAGE) {
                continue;
            }
            double score = coverage * 10 - page.path("index").asInt(15) * 0.2 + Math.min(w, 3000) / 1000.0;
            if (score > bestScore) {
                best = page;
                bestScore = score;
            }
        }
        if (best != null) {
            JsonNode info = best.path("imageinfo").path(0);
            JsonNode meta = info.path("extmetadata");
            String license = meta.path("LicenseShortName").path("value").asString("").strip();
            String file = info.path("thumburl").asString(info.path("url").asString(""));
            String pageUrl = info.path("descriptionurl").asString("");
            String artist = stripHtml(meta.path("Artist").path("value").asString("unknown author"));
            String title = title(best);
            Path out = target.resolveSibling(target.getFileName() + ".jpg");
            Path cached = download(file, ".img");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", cached.toString(), "-vf",
                    "scale=-2:" + PRINT_HEIGHT + ":flags=lanczos,unsharp=5:5:0.5:5:5:0", "-q:v", "2", out.toString()));
            String type = licenseType(license);
            String evidence = "\"" + title + "\" by " + artist + ", " + license + ", via Wikimedia Commons: " + pageUrl;
            LicenseRecord record = new LicenseRecord(type, "Wikimedia Commons: " + artist, pageUrl, evidence, null);
            used.add(pageUrl);
            return Optional.of(new Found(out, RealMedia.PHOTO, "wikimedia-commons", record,
                    "Photo: " + shorten(artist, 40) + " · " + license, pageUrl,
                    shorten(title + " — " + description(meta), 300)));
        }
        return Optional.empty();
    }

    private static String title(JsonNode page) {
        return page.path("title").asString("").replaceFirst("^File:", "").replaceFirst("\\.[A-Za-z]+$", "");
    }

    private static String description(JsonNode meta) {
        return stripHtml(meta.path("ImageDescription").path("value").asString(""));
    }

    /** Share of the query's words (accents folded, 3+ letters) found in the media's title and description. */
    static double coverage(String query, String text) {
        String haystack = " " + fold(text) + " ";
        List<String> tokens = List.of(fold(query).split(" ")).stream().filter(t -> t.length() >= 3).toList();
        if (tokens.isEmpty()) {
            return 0;
        }
        return (double) tokens.stream().filter(t -> haystack.contains(" " + t)).count() / tokens.size();
    }

    private static String fold(String text) {
        return java.text.Normalizer.normalize(text == null ? "" : text, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private Optional<Found> pexels(String query, double seconds, int width, int height, Set<String> used,
                                   Path target) throws IOException, InterruptedException {
        String url = "https://api.pexels.com/v1/videos/search?orientation=landscape&size=medium&per_page=15&query="
                + enc(query);
        JsonNode videos = get(url, pexelsKey).path("videos");
        for (JsonNode video : videos) {
            String page = video.path("url").asString("");
            double duration = video.path("duration").asDouble(0);
            if (used.contains(page) || duration < Math.min(seconds, 4)) {
                continue;
            }
            JsonNode best = null;
            for (JsonNode file : video.path("video_files")) {
                int w = file.path("width").asInt(0);
                if (!"video/mp4".equals(file.path("file_type").asString("")) || w < 1280) {
                    continue;
                }
                if (best == null || Math.abs(w - width) < Math.abs(best.path("width").asInt() - width)) {
                    best = file;
                }
            }
            if (best == null) {
                continue;
            }
            Path cached = download(best.path("link").asString(), ".mp4");
            Path out = target.resolveSibling(target.getFileName() + ".mp4");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-stream_loop", "-1", "-i", cached.toString(),
                    "-t", String.format(Locale.ROOT, "%.3f", seconds + 0.6), "-vf",
                    "scale=" + width + ":" + height + ":force_original_aspect_ratio=increase,crop=" + width + ":"
                            + height + ",fps=30", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                    "-pix_fmt", "yuv420p", out.toString()));
            String author = video.path("user").path("name").asString("Pexels contributor");
            String evidence = "Video by " + author + " on Pexels: " + page;
            used.add(page);
            return Optional.of(new Found(out, RealMedia.FOOTAGE, "pexels",
                    new LicenseRecord("pexels-license", "Pexels: " + author, page, evidence, null),
                    "Video: " + shorten(author, 40) + " / Pexels", page,
                    page.replaceAll(".*/video/", "").replaceAll("-\\d+/?$", "").replace('-', ' ')));
        }
        return Optional.empty();
    }

    static boolean allowed(String license) {
        String l = license.toLowerCase(Locale.ROOT).strip();
        return ALLOWED_LICENSE.matcher(l).matches();
    }

    static String licenseType(String license) {
        String l = license.toLowerCase(Locale.ROOT).strip();
        if (l.startsWith("public domain") || l.startsWith("pd")) {
            return "public_domain";
        }
        return l.replace(' ', '-');
    }

    private JsonNode get(String url, String authorization) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", USER_AGENT);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + URI.create(url).getHost());
        }
        return json.readTree(response.body());
    }

    private Path download(String url, String extension) throws IOException, InterruptedException {
        Files.createDirectories(cache);
        Path file = cache.resolve(sha1(url) + extension);
        if (Files.isRegularFile(file) && Files.size(file) > 0) {
            return file;
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3))
                .header("User-Agent", USER_AGENT).build();
        Path partial = cache.resolve(file.getFileName() + ".part");
        HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() != 200) {
            Files.deleteIfExists(partial);
            throw new IOException("Download failed: HTTP " + response.statusCode());
        }
        Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING);
        return file;
    }

    static String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ").replace("&nbsp;", " ").replace(' ', ' ').replace("&amp;", "&").replaceAll("\\s+", " ")
                .strip();
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String sha1(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
