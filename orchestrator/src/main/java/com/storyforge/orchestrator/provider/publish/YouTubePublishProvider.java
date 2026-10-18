package com.storyforge.orchestrator.provider.publish;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.Publication;
import com.storyforge.orchestrator.youtube.YouTubeClient;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "publish", havingValue = "youtube")
public class YouTubePublishProvider implements PublishProvider {

    private static final Logger log = LoggerFactory.getLogger(YouTubePublishProvider.class);

    private final YouTubeClient youtube;
    private final StoryForgeProperties.YouTube config;

    public YouTubePublishProvider(YouTubeClient youtube, StoryForgeProperties properties) {
        this.youtube = youtube;
        this.config = properties.youtube();
    }

    @Override
    public String name() {
        return "youtube";
    }

    @Override
    public Publication publish(PublishRequest request) {
        String videoId = youtube.upload(request.video(), new YouTubeClient.VideoMetadata(request.title(),
                request.description(), request.tags(), config.categoryId(), config.language(), request.publishAt(),
                request.disclosure()));
        List<String> warnings = new ArrayList<>();
        try {
            youtube.setThumbnail(videoId, jpeg(request.thumbnail()), "image/jpeg");
        } catch (RuntimeException e) {
            log.warn("Thumbnail upload failed for {}: {}", videoId, e.getMessage());
            warnings.add("Thumbnail not set (verify the channel by phone to allow custom thumbnails): "
                    + e.getMessage());
        }
        try {
            youtube.insertCaptions(videoId, request.captions(), config.language(), "English");
        } catch (RuntimeException e) {
            log.warn("Captions upload failed for {}: {}", videoId, e.getMessage());
            warnings.add("Captions not uploaded: " + e.getMessage());
        }
        return new Publication(name(), videoId, "https://studio.youtube.com/video/" + videoId + "/edit", "private",
                Instant.now(), request.publishAt(), request.disclosure(), warnings);
    }

    private static Path jpeg(Path png) {
        Path out = png.resolveSibling(png.getFileName().toString().replaceFirst("\\.png$", "") + ".jpg");
        try {
            BufferedImage source = ImageIO.read(png.toFile());
            BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
            rgb.getGraphics().drawImage(source, 0, 0, null);
            if (!ImageIO.write(rgb, "jpg", out.toFile())) {
                throw new IOException("No JPEG writer");
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
