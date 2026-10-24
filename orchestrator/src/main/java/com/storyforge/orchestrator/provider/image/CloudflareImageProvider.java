package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.LicenseRecord;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "image", havingValue = "cloudflare")
public class CloudflareImageProvider implements ImageProvider {

    private static final String COMPOSITION_SUFFIX = ", wide cinematic composition, main subject in the center";
    private static final String SAFETY_SUFFIX = ", no text, no letters, no watermark, no logo";
    private static final String LICENSE_URL = "https://huggingface.co/black-forest-labs/FLUX.1-schnell";

    private final StoryForgeProperties.Cloudflare config;
    private final RestClient client;
    private final Path cacheDir;
    private final ImageFinisher finisher;
    private final ImageQuota quota;

    public CloudflareImageProvider(StoryForgeProperties properties, ImageFinisher finisher, ImageQuota quota) {
        this.quota = quota;
        this.config = properties.cloudflare();
        this.finisher = finisher;
        if (config.accountId() == null || config.accountId().isBlank()
                || config.apiToken() == null || config.apiToken().isBlank()) {
            throw new IllegalStateException("Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN in .env");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(config.timeoutSeconds()));
        this.client = RestClient.builder()
                .baseUrl("https://api.cloudflare.com/client/v4/accounts/" + config.accountId() + "/ai/run/")
                .defaultHeader("Authorization", "Bearer " + config.apiToken())
                .requestFactory(factory)
                .build();
        this.cacheDir = Path.of(properties.dataDir()).toAbsolutePath().resolve("cache").resolve("images");
    }

    @Override
    public String name() {
        return "cloudflare";
    }

    @Override
    public boolean isCached(ImageRequest request) {
        return Files.isRegularFile(cacheDir.resolve(cacheKey(request) + ".jpg"));
    }

    @Override
    public int remainingToday() {
        return quota.remaining();
    }

    @Override
    public int dailyLimit() {
        return quota.dailyLimit();
    }

    private String cacheKey(ImageRequest request) {
        return hash(config.imageModel() + "\u0000" + request.prompt() + SAFETY_SUFFIX + "\u0000" + request.seed());
    }

    @Override
    public ImageResult generate(ImageRequest request, Path outputImage) {
        String prompt = request.prompt() + COMPOSITION_SUFFIX + SAFETY_SUFFIX;
        String key = cacheKey(request);
        Path raw = cacheDir.resolve(key + ".jpg");
        Path finished = cacheDir.resolve(key + "_" + request.width() + "x" + request.height() + ".png");
        try {
            if (!Files.isRegularFile(finished)) {
                Files.createDirectories(cacheDir);
                if (!Files.isRegularFile(raw)) {
                    if (quota.remaining() == 0) {
                        throw new QuotaExhaustedException("Cloudflare daily free image quota used up (resets 00:00 UTC)");
                    }
                    Files.write(raw, fetch(prompt));
                    quota.recordCall();
                }
                finisher.finish(raw, finished, request.width(), request.height());
            }
            finisher.copyCached(finished, outputImage);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new ImageResult(name(), config.imageModel(), BigDecimal.ZERO, false,
                new LicenseRecord("generated", "cloudflare-workers-ai:" + config.imageModel(), LICENSE_URL,
                        "FLUX.1-schnell (Apache-2.0) via Cloudflare Workers AI free tier", null));
    }

    private byte[] fetch(String prompt) {
        try {
            JsonNode response = client.post().uri(config.imageModel())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("prompt", prompt, "steps", config.steps()))
                    .retrieve().body(JsonNode.class);
            String image = response == null ? "" : response.path("result").path("image").asString("");
            if (image.isEmpty()) {
                throw new MediaException("Cloudflare returned no image: " + response);
            }
            return Base64.getDecoder().decode(image);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 429) {
                quota.markExhausted();
                throw new QuotaExhaustedException("Cloudflare daily free image quota used up (resets 00:00 UTC): "
                        + e.getResponseBodyAsString(StandardCharsets.UTF_8));
            }
            throw new MediaException("Cloudflare image failed (" + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(StandardCharsets.UTF_8), e);
        }
    }

    private static String hash(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
