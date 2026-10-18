package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.media.MediaException;
import com.storyforge.orchestrator.model.LicenseRecord;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
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
import javax.imageio.ImageIO;
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

    private static final String SAFETY_SUFFIX = ", no text, no letters, no watermark, no logo";
    private static final String LICENSE_URL = "https://huggingface.co/black-forest-labs/FLUX.1-schnell";

    private final StoryForgeProperties.Cloudflare config;
    private final RestClient client;
    private final Path cacheDir;

    public CloudflareImageProvider(StoryForgeProperties properties) {
        this.config = properties.cloudflare();
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
    public ImageResult generate(ImageRequest request, Path outputImage) {
        String prompt = request.prompt() + SAFETY_SUFFIX;
        Path cached = cacheDir.resolve(hash(config.imageModel() + "\u0000" + prompt + "\u0000" + request.seed())
                + ".jpg");
        try {
            if (!Files.isRegularFile(cached)) {
                Files.createDirectories(cacheDir);
                Files.write(cached, fetch(prompt));
            }
            BufferedImage decoded = ImageIO.read(cached.toFile());
            if (decoded == null
                    || !ImageIO.write(cover(decoded, request.width(), request.height()), "png", outputImage.toFile())) {
                throw new MediaException("Cannot convert Cloudflare image to PNG: " + cached);
            }
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
            throw new MediaException("Cloudflare image failed (" + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString(StandardCharsets.UTF_8), e);
        }
    }

    static BufferedImage cover(BufferedImage source, int width, int height) {
        double scale = Math.max((double) width / source.getWidth(), (double) height / source.getHeight());
        int scaledWidth = (int) Math.ceil(source.getWidth() * scale);
        int scaledHeight = (int) Math.ceil(source.getHeight() * scale);
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, (width - scaledWidth) / 2, (height - scaledHeight) / 2, scaledWidth, scaledHeight, null);
        g.dispose();
        return target;
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
