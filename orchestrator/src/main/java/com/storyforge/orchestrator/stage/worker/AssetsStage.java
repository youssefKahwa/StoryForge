package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AssetManifest.Asset;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.image.ImageProvider;
import com.storyforge.orchestrator.provider.image.ImageRequest;
import com.storyforge.orchestrator.provider.image.ImageResult;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import com.storyforge.orchestrator.visual.CardRenderer;
import java.math.BigDecimal;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AssetsStage implements StageWorker {

    private static final String GRAPHICS = "storyforge-graphics";
    private static final Logger log = LoggerFactory.getLogger(AssetsStage.class);

    private final ImageProvider images;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Video video;

    public AssetsStage(ImageProvider images, FormatRegistry formats, StoryForgeProperties properties) {
        this.images = images;
        this.formats = formats;
        this.video = properties.video();
    }

    private ImageResult fallback(StageContext context, Shot shot, Path file, List<Asset> assets,
                                 RuntimeException cause) {
        Asset donor = null;
        for (int i = assets.size() - 1; i >= 0 && donor == null; i--) {
            if (assets.get(i).provider().equals(images.name())) {
                donor = assets.get(i);
            }
        }
        if (donor != null) {
            log.warn("Run {} {}: image provider failed ({}), reusing {}", context.run().id(), shot.id(),
                    cause.getMessage(), donor.shotId());
            try {
                Files.copy(context.files().root().resolve(donor.file()), file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new ImageResult("reused-fallback", donor.model(), BigDecimal.ZERO, donor.realistic(),
                    donor.license());
        }
        log.warn("Run {} {}: image provider failed, using a placeholder: {}", context.run().id(), shot.id(),
                cause.getMessage());
        CardRenderer.placeholder(file, video.width(), video.height(), shot.seed(), shot.type(), shot.prompt());
        return new ImageResult("placeholder-fallback", "java2d", BigDecimal.ZERO, false,
                LicenseRecord.generated("storyforge-placeholder"));
    }

    @Override
    public Stage stage() {
        return Stage.ASSETS;
    }

    @Override
    public List<String> outputs() {
        return List.of("assets.json");
    }

    @Override
    public void execute(StageContext context) {
        ShotList shots = context.files().read(Stage.VISUAL_PLAN, "shotlist.json", ShotList.class);
        FormatDefinition.Style style = formats.get(context.run().format()).style();
        List<Asset> assets = new ArrayList<>();
        int fallbacks = 0;
        for (Shot shot : shots.shots()) {
            String name = shot.id() + ".png";
            Path file = context.files().path(Stage.ASSETS, name);
            String relative = context.files().relative(file);
            if (shot.graphic()) {
                CardRenderer.kineticText(file, video.width(), video.height(), shot.seed(), shot.text());
                context.usage().provider(GRAPHICS);
                assets.add(new Asset(shot.id(), relative, shot.type(), GRAPHICS, "java2d", null, shot.seed(),
                        BigDecimal.ZERO, false, LicenseRecord.generated(GRAPHICS)));
                continue;
            }
            ImageResult result;
            try {
                result = images.generate(new ImageRequest(shot.prompt(), style.negativePrompt(), shot.seed(),
                        video.width(), video.height(), shot.type(), shot.type() + " / scene " + shot.sceneId()), file);
            } catch (RuntimeException e) {
                result = fallback(context, shot, file, assets, e);
                fallbacks++;
            }
            if (result.costUsd().signum() > 0) {
                context.usage().call(result.provider(), 0, 0, result.costUsd());
            } else {
                context.usage().provider(result.provider());
            }
            assets.add(new Asset(shot.id(), relative, shot.type(), result.provider(), result.model(), shot.prompt(),
                    shot.seed(), result.costUsd(), result.realistic(), result.license()));
        }
        if (fallbacks > 0) {
            log.warn("Run {}: {} of {} images are placeholders", context.run().id(), fallbacks, shots.shots().size());
        }
        boolean realistic = assets.stream().anyMatch(Asset::realistic);
        context.files().write(Stage.ASSETS, "assets.json", new AssetManifest(realistic, assets));
    }
}
