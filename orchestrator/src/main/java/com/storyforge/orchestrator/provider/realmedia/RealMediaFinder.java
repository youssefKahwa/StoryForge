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
    /** Correlation of two small grey frames above which they are the same picture, whatever the page says. */
    private static final double SAME_PICTURE = 0.97;

    private final FfmpegService ffmpeg;
    private final JsonMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final boolean enabled;
    private final String pexelsKey;
    private final String pixabayKey;
    private final Path cache;

    public RealMediaFinder(FfmpegService ffmpeg, JsonMapper json, StoryForgeProperties properties,
                           @Value("${storyforge.real-media.enabled:false}") boolean enabled,
                           @Value("${storyforge.real-media.pexels-api-key:}") String pexelsKey,
                           @Value("${storyforge.real-media.pixabay-api-key:}") String pixabayKey) {
        this.ffmpeg = ffmpeg;
        this.json = json;
        this.enabled = enabled;
        this.pexelsKey = pexelsKey == null ? "" : pexelsKey.strip();
        this.pixabayKey = pixabayKey == null ? "" : pixabayKey.strip();
        this.cache = Path.of(properties.dataDir()).toAbsolutePath().resolve("library").resolve("media-cache");
    }

    public boolean enabled() {
        return enabled;
    }

    /** True when a stock video library with a key is configured (Wikimedia Commons video needs none). */
    public boolean footageAvailable() {
        return enabled && (!pexelsKey.isEmpty() || !pixabayKey.isEmpty());
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
            if (RealMedia.FOOTAGE.equals(request.kind())) {
                Optional<Found> footage = Optional.empty();
                if (!pexelsKey.isEmpty()) {
                    footage = pexels(request.query(), seconds, width, height, used, target);
                }
                if (footage.isEmpty() && !pixabayKey.isEmpty()) {
                    footage = pixabay(request.query(), seconds, width, height, used, target);
                }
                if (footage.isEmpty()) {
                    footage = commonsVideo(request.query(), seconds, width, height, used, target);
                }
                if (footage.isPresent()) {
                    return footage;
                }
            }
            if (!RealMedia.PHOTO.equals(request.kind())) {
                return Optional.empty();
            }
            Optional<Found> photo = commons(request.query(), used, target);
            if (photo.isEmpty()) {
                photo = nasa(request.query(), used, target);
            }
            if (photo.isEmpty()) {
                photo = openverse(request.query(), used, target);
            }
            return photo.isPresent() || pixabayKey.isEmpty() ? photo : pixabayPhoto(request.query(), used, target);
        } catch (IOException | RuntimeException e) {
            log.warn("Real media search failed for '{}': {}", request.query(), e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * True when this file shows a picture already in {@code seen}: the same upload often lives under several pages
     * or sites, which the page check cannot tell. Otherwise it is added to {@code seen}.
     */
    public boolean repeatsEarlier(Path file, List<float[]> seen) {
        try {
            Path raw = Files.createTempFile("storyforge-picture", ".gray");
            try {
                ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", file.toString(), "-frames:v", "1", "-vf",
                        "scale=16:9,format=gray", "-f", "rawvideo", raw.toString()));
                float[] picture = unit(Files.readAllBytes(raw));
                for (float[] earlier : seen) {
                    double dot = 0;
                    for (int i = 0; i < picture.length && i < earlier.length; i++) {
                        dot += picture[i] * earlier[i];
                    }
                    if (dot >= SAME_PICTURE) {
                        log.info("Real media {} shows a picture already used, passed over", file.getFileName());
                        return true;
                    }
                }
                seen.add(picture);
                return false;
            } finally {
                Files.deleteIfExists(raw);
            }
        } catch (IOException | RuntimeException e) {
            log.debug("Picture comparison skipped for {}: {}", file, e.getMessage());
            return false;
        }
    }

    /** Zero mean and unit length, so a brighter copy still matches; a flat picture becomes zero and matches nothing. */
    static float[] unit(byte[] grey) {
        double mean = 0;
        for (byte value : grey) {
            mean += value & 0xFF;
        }
        mean /= Math.max(1, grey.length);
        float[] out = new float[grey.length];
        double norm = 0;
        for (int i = 0; i < grey.length; i++) {
            out[i] = (float) ((grey[i] & 0xFF) - mean);
            norm += out[i] * out[i];
        }
        norm = Math.sqrt(norm);
        for (int i = 0; norm > 1 && i < out.length; i++) {
            out[i] /= (float) norm;
        }
        return norm > 1 ? out : new float[grey.length];
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
                            + height + ",fps=30", "-map", "0:v:0", "-map_metadata", "-1", "-write_tmcd", "0", "-an", "-dn", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
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

    /**
     * Pixabay videos (Pixabay Content License: commercial use, no attribution required; we credit anyway).
     * https://pixabay.com/api/docs/ (checked 2026-10-20): GET /api/videos/, search responses cached 24 h.
     */
    private Optional<Found> pixabay(String query, double seconds, int width, int height, Set<String> used,
                                    Path target) throws IOException, InterruptedException {
        String url = "https://pixabay.com/api/videos/?video_type=film&safesearch=true&per_page=20&key="
                + enc(pixabayKey) + "&q=" + enc(query);
        JsonNode hits = cachedJson("pixabay-" + sha1(query), url).path("hits");
        for (JsonNode hit : hits) {
            String page = hit.path("pageURL").asString("");
            double duration = hit.path("duration").asDouble(0);
            if (used.contains(page) || duration < Math.min(seconds, 4)
                    || coverage(query, hit.path("tags").asString("")) < 0.5) {
                continue;
            }
            // medium is usually 1920x1080 (all videos have it), large can be 4K; landscape only (no phone clips).
            JsonNode file = null;
            for (String size : List.of("medium", "large", "small")) {
                JsonNode candidate = hit.path("videos").path(size);
                int w = candidate.path("width").asInt(0);
                int h = candidate.path("height").asInt(1);
                if (!candidate.path("url").asString("").isEmpty() && w >= 1280 && w >= h * 1.3) {
                    file = candidate;
                    break;
                }
            }
            if (file == null) {
                continue;
            }
            String link = file.path("url").asString("");
            Path out = cut(download(link, ".mp4"), seconds, width, height, target);
            String author = hit.path("user").asString("Pixabay contributor");
            used.add(page);
            return Optional.of(new Found(out, RealMedia.FOOTAGE, "pixabay",
                    new LicenseRecord("pixabay-license", "Pixabay: " + author, page,
                            "Video by " + author + " on Pixabay: " + page, null),
                    "Video: " + shorten(author, 40) + " / Pixabay", page, hit.path("tags").asString("")));
        }
        return Optional.empty();
    }

    public boolean stockAvailable() {
        return enabled && !pixabayKey.isEmpty();
    }

    /** Stock photo for a search the LLM proposed for a picture that could not be generated. */
    public Optional<Found> stockPhoto(String query, Set<String> used, Path target) {
        if (!stockAvailable() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        try {
            return pixabayPhoto(query, used, target);
        } catch (IOException | RuntimeException e) {
            log.warn("Stock photo search failed for '{}': {}", query, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * NASA Image and Video Library (no key): NASA's own media is public domain; anything credited to a third party
     * or marked copyrighted is skipped. https://images.nasa.gov/docs/images.nasa.gov_api_docs.pdf (2026-10-20).
     */
    private Optional<Found> nasa(String query, Set<String> used, Path target) throws IOException, InterruptedException {
        String url = "https://images-api.nasa.gov/search?media_type=image&page_size=15&q=" + enc(query);
        for (JsonNode item : cachedJson("nasa-" + sha1(query), url).path("collection").path("items")) {
            JsonNode data = item.path("data").path(0);
            String id = data.path("nasa_id").asString("");
            String title = data.path("title").asString("");
            String description = data.path("description").asString("");
            String keywords = data.path("keywords").toString();
            String lower = (title + " " + description).toLowerCase(Locale.ROOT);
            String preview = item.path("links").path(0).path("href").asString("");
            String page = "https://images.nasa.gov/details/" + enc(id);
            if (id.isEmpty() || preview.isEmpty() || used.contains(page) || lower.contains("copyright")
                    || lower.contains("©") || lower.contains("courtesy of")
                    || coverage(query, title + " " + description + " " + keywords) < MIN_QUERY_COVERAGE) {
                continue;
            }
            String large = preview.replaceFirst("~(thumb|small|medium)\\.jpg$", "~large.jpg");
            Path image;
            try {
                image = download(large, ".img");
            } catch (IOException e) {
                image = download(preview, ".img");
            }
            Path out = target.resolveSibling(target.getFileName() + ".jpg");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", image.toString(), "-vf",
                    "scale=-2:" + PRINT_HEIGHT + ":flags=lanczos,unsharp=5:5:0.5:5:5:0", "-q:v", "2", out.toString()));
            String center = data.path("center").asString("NASA");
            String evidence = "\"" + title + "\" — NASA/" + center + ", public domain (NASA media guidelines): " + page;
            used.add(page);
            return Optional.of(new Found(out, RealMedia.PHOTO, "nasa",
                    new LicenseRecord("public_domain", "NASA/" + center, page, evidence, "US"),
                    "Photo: NASA/" + center, page, shorten(title + " — " + description, 300)));
        }
        return Optional.empty();
    }

    /**
     * Openverse (api.openverse.org, no key): openly licensed photos from Flickr, museums, rawpixel and more, beyond
     * Commons (searched directly before, so excluded here). Only CC0, the Public Domain Mark and CC BY: share-alike
     * and non-commercial terms never reach a monetized video. Anonymous use allows 200 searches a day, so searches
     * are cached for 24 h and capped at {@link #OPENVERSE_DAILY} a day.
     */
    private Optional<Found> openverse(String query, Set<String> used, Path target)
            throws IOException, InterruptedException {
        String key = "openverse-" + sha1(query);
        if (!cached(key) && !openverseBudget()) {
            return Optional.empty();
        }
        // No size or aspect filter: most Flickr results are 1024 px "_b" files, enough for the print style, and the
        // filters left 0-1 results for scene searches (tested 2026-11-20); width and shape are checked per result.
        String url = "https://api.openverse.org/v1/images/?q=" + enc(query) + "&license=cc0,pdm,by"
                + "&mature=false&page_size=20&excluded_source=wikimedia";
        for (JsonNode hit : cachedJson(key, url).path("results")) {
            Optional<OpenverseHit> pick = OpenverseHit.of(hit);
            if (pick.isEmpty() || used.contains(pick.get().page())
                    || coverage(query, pick.get().title() + " " + pick.get().tags()) < MIN_QUERY_COVERAGE) {
                continue;
            }
            OpenverseHit h = pick.get();
            Path image;
            try {
                image = download(h.url(), ".img");
            } catch (IOException e) {
                continue;
            }
            Path out = target.resolveSibling(target.getFileName() + ".jpg");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", image.toString(), "-vf",
                    "scale=-2:" + PRINT_HEIGHT + ":flags=lanczos,unsharp=5:5:0.5:5:5:0", "-q:v", "2", out.toString()));
            used.add(h.page());
            return Optional.of(new Found(out, RealMedia.PHOTO, "openverse",
                    new LicenseRecord(h.licenseType(), "Openverse/" + h.source() + ": " + h.creator(), h.page(),
                            h.attribution(), null),
                    "Photo: " + shorten(h.creator(), 40) + " / " + h.source() + " · " + h.licenseLabel(), h.page(),
                    shorten(h.title() + " — " + h.tags(), 300)));
        }
        return Optional.empty();
    }

    /** One Openverse image result we may use: its licence is CC0, PDM or CC BY and it has a picture and a page. */
    record OpenverseHit(String url, String page, String title, String creator, String source, String license,
                        String version, String attribution, String tags) {

        static Optional<OpenverseHit> of(JsonNode hit) {
            String license = hit.path("license").asString("").toLowerCase(Locale.ROOT);
            String url = hit.path("url").asString("");
            String page = hit.path("foreign_landing_url").asString("");
            int width = hit.path("width").asInt(0);
            if (!Set.of("cc0", "pdm", "by").contains(license) || url.isEmpty() || page.isEmpty()
                    || hit.path("mature").asBoolean(false) || width < OPENVERSE_MIN_WIDTH
                    || width < hit.path("height").asInt(0)) {
                return Optional.empty();
            }
            List<String> tags = new ArrayList<>();
            hit.path("tags").forEach(t -> tags.add(t.path("name").asString("")));
            String creator = hit.path("creator").asString("");
            return Optional.of(new OpenverseHit(url, page, hit.path("title").asString(""),
                    creator.isBlank() ? "unknown author" : creator, hit.path("source").asString("openverse"),
                    license, hit.path("license_version").asString(""), hit.path("attribution").asString(""),
                    String.join(" ", tags)));
        }

        String licenseType() {
            return switch (license) {
                case "cc0" -> "cc0";
                case "pdm" -> "public_domain";
                default -> "cc-by";
            };
        }

        String licenseLabel() {
            return switch (license) {
                case "cc0" -> "CC0";
                case "pdm" -> "Public domain";
                default -> "CC BY " + version;
            };
        }
    }

    static final int OPENVERSE_DAILY = 150;
    /** Flickr's "_b" files are 1024 px: shown as a print 820 px high, that is enough. */
    static final int OPENVERSE_MIN_WIDTH = 1000;
    private int openverseCalls;
    private long openverseDay;

    private synchronized boolean openverseBudget() {
        long day = System.currentTimeMillis() / Duration.ofDays(1).toMillis();
        if (day != openverseDay) {
            openverseDay = day;
            openverseCalls = 0;
        }
        return openverseCalls++ < OPENVERSE_DAILY;
    }

    private boolean cached(String key) throws IOException {
        Path file = cache.resolve(key + ".json");
        return Files.isRegularFile(file)
                && Files.getLastModifiedTime(file).toMillis() > System.currentTimeMillis() - Duration.ofHours(24).toMillis();
    }

    /**
     * Pixabay photos as a fallback when Commons has nothing (Content License; downloaded, never hotlinked, as the API
     * rules require). largeImageURL is at most 1280 px, enough for the print style.
     */
    private Optional<Found> pixabayPhoto(String query, Set<String> used, Path target)
            throws IOException, InterruptedException {
        String url = "https://pixabay.com/api/?image_type=photo&orientation=horizontal&safesearch=true&per_page=20"
                + "&min_width=1200&key=" + enc(pixabayKey) + "&q=" + enc(query);
        JsonNode hits = cachedJson("pixabay-photo-" + sha1(query), url).path("hits");
        for (JsonNode hit : hits) {
            String page = hit.path("pageURL").asString("");
            String link = hit.path("largeImageURL").asString("");
            String tags = hit.path("tags").asString("");
            if (link.isEmpty() || used.contains(page) || coverage(query, tags) < 0.5) {
                continue;
            }
            Path out = target.resolveSibling(target.getFileName() + ".jpg");
            ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-i", download(link, ".img").toString(), "-vf",
                    "scale=-2:" + PRINT_HEIGHT + ":flags=lanczos,unsharp=5:5:0.5:5:5:0", "-q:v", "2", out.toString()));
            String author = hit.path("user").asString("Pixabay contributor");
            used.add(page);
            return Optional.of(new Found(out, RealMedia.PHOTO, "pixabay",
                    new LicenseRecord("pixabay-license", "Pixabay: " + author, page,
                            "Photo by " + author + " on Pixabay: " + page, null),
                    "Photo: " + shorten(author, 40) + " / Pixabay", page, tags));
        }
        return Optional.empty();
    }

    /** Free-licensed videos on Wikimedia Commons (no key); same license rules as photos. */
    private Optional<Found> commonsVideo(String query, double seconds, int width, int height, Set<String> used,
                                         Path target) throws IOException, InterruptedException {
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json&generator=search"
                + "&gsrnamespace=6&gsrlimit=10&gsrsearch=" + enc(query + " filetype:video")
                + "&prop=imageinfo&iiprop=url%7Cmime%7Csize%7Cextmetadata"
                + "&iiextmetadatafilter=LicenseShortName%7CArtist%7CRestrictions%7CImageDescription";
        JsonNode pages = get(url, null).path("query").path("pages");
        List<JsonNode> ordered = new ArrayList<>();
        pages.forEach(ordered::add);
        ordered.sort(Comparator.comparingInt(p -> p.path("index").asInt(999)));
        for (JsonNode page : ordered) {
            JsonNode info = page.path("imageinfo").path(0);
            JsonNode meta = info.path("extmetadata");
            String license = meta.path("LicenseShortName").path("value").asString("").strip();
            String pageUrl = info.path("descriptionurl").asString("");
            double duration = info.path("duration").asDouble(0);
            if (info.path("width").asInt(0) < 1280 || duration < Math.min(seconds, 3) || !allowed(license)
                    || !meta.path("Restrictions").path("value").asString("").isBlank() || used.contains(pageUrl)
                    || coverage(query, title(page) + " " + description(meta)) < MIN_QUERY_COVERAGE) {
                continue;
            }
            Path out = cut(download(info.path("url").asString(), ".video"), seconds, width, height, target);
            String artist = stripHtml(meta.path("Artist").path("value").asString("unknown author"));
            String evidence = "\"" + title(page) + "\" by " + artist + ", " + license + ", via Wikimedia Commons: "
                    + pageUrl;
            used.add(pageUrl);
            return Optional.of(new Found(out, RealMedia.FOOTAGE, "wikimedia-commons",
                    new LicenseRecord(licenseType(license), "Wikimedia Commons: " + artist, pageUrl, evidence, null),
                    "Video: " + shorten(artist, 40) + " · " + license, pageUrl,
                    shorten(title(page) + " — " + description(meta), 300)));
        }
        return Optional.empty();
    }

    /** Exact-length, muted 1080p clip that loops the source when it is shorter than the shot. */
    private Path cut(Path source, double seconds, int width, int height, Path target) {
        Path out = target.resolveSibling(target.getFileName() + ".mp4");
        ffmpeg.ffmpeg(List.of("-y", "-loglevel", "error", "-stream_loop", "-1", "-i", source.toString(),
                "-t", String.format(Locale.ROOT, "%.3f", seconds + 0.6), "-vf",
                "scale=" + width + ":" + height + ":force_original_aspect_ratio=increase,crop=" + width + ":"
                        + height + ",fps=30", "-map", "0:v:0", "-map_metadata", "-1", "-write_tmcd", "0", "-an", "-dn", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
                "-pix_fmt", "yuv420p", out.toString()));
        return out;
    }

    private JsonNode cachedJson(String key, String url) throws IOException, InterruptedException {
        Files.createDirectories(cache);
        Path file = cache.resolve(key + ".json");
        if (Files.isRegularFile(file)
                && Files.getLastModifiedTime(file).toMillis() > System.currentTimeMillis() - Duration.ofHours(24).toMillis()) {
            return json.readTree(Files.readString(file));
        }
        JsonNode node = get(url, null);
        Files.writeString(file, json.writeValueAsString(node));
        return node;
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

    private long lastPixabayCall;
    private long lastOpenverseCall;

    /** Pixabay allows 100 requests a minute and Openverse 20 without a key; keep a little under both. */
    private synchronized void throttle(String url) throws InterruptedException {
        boolean pixabay = url.contains("pixabay.com");
        boolean openverse = url.contains("api.openverse.org");
        if (!pixabay && !openverse) {
            return;
        }
        long last = pixabay ? lastPixabayCall : lastOpenverseCall;
        long wait = last + (pixabay ? 650 : 3200) - System.currentTimeMillis();
        if (wait > 0) {
            Thread.sleep(wait);
        }
        if (pixabay) {
            lastPixabayCall = System.currentTimeMillis();
        } else {
            lastOpenverseCall = System.currentTimeMillis();
        }
    }

    private JsonNode get(String url, String authorization) throws IOException, InterruptedException {
        throttle(url);
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
