package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AssetManifest.Asset;
import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.model.RealMedia;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.provider.image.ImageProvider;
import com.storyforge.orchestrator.provider.image.ImageRequest;
import com.storyforge.orchestrator.provider.image.ImageResult;
import com.storyforge.orchestrator.provider.llm.LlmException;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.provider.realmedia.RealMediaFinder;
import com.storyforge.orchestrator.revision.RevisionService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AssetsStage implements StageWorker {

    private static final String GRAPHICS = "storyforge-graphics";
    private static final Logger log = LoggerFactory.getLogger(AssetsStage.class);

    private final ImageProvider images;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Video video;

    private final RealMediaFinder finder;
    private final LlmRouter llm;

    public AssetsStage(ImageProvider images, FormatRegistry formats, StoryForgeProperties properties,
                       RealMediaFinder finder, LlmRouter llm) {
        this.finder = finder;
        this.llm = llm;
        this.images = images;
        this.formats = formats;
        this.video = properties.video();
    }

    private ImageRequest request(Shot shot, FormatDefinition.Style style) {
        return new ImageRequest(shot.prompt(), style.negativePrompt(), shot.seed(), video.width(), video.height(),
                shot.type(), shot.type() + " / scene " + shot.sceneId());
    }

    private Set<String> budget(StageContext context, ShotList shots, FormatDefinition.Style style,
                               Set<String> realShots) {
        List<Shot> misses = shots.shots().stream().filter(shot -> !shot.graphic())
                .filter(shot -> !realShots.contains(shot.id()))
                .filter(shot -> !images.isCached(request(shot, style))).toList();
        int remaining = images.remainingToday();
        Set<String> allowed = new HashSet<>();
        if (misses.size() <= remaining) {
            misses.forEach(shot -> allowed.add(shot.id()));
            return allowed;
        }
        for (int i = 0; i < remaining; i++) {
            allowed.add(misses.get((int) ((long) i * misses.size() / remaining)).id());
        }
        log.warn("Run {}: {} new images needed but only {} left in today's free quota; spreading them and reusing "
                + "the rest", context.run().id(), misses.size(), remaining);
        return allowed;
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

    /** Real photos and footage for the shots the director picked; anything not found falls back to AI images. */
    private Map<String, RealMediaFinder.Found> realMedia(StageContext context, ShotList shots) {
        Map<String, RealMediaFinder.Found> found = new HashMap<>();
        if (!finder.enabled() || !context.files().exists(Stage.VISUAL_PLAN, VisualPlanStage.REAL_MEDIA)) {
            return found;
        }
        Map<String, RealMedia> picks = context.files().read(Stage.VISUAL_PLAN, VisualPlanStage.REAL_MEDIA,
                new TypeReference<Map<String, RealMedia>>() { });
        Set<String> used = new HashSet<>();
        for (Shot shot : shots.shots()) {
            RealMedia pick = picks.get(shot.id());
            if (pick == null || shot.graphic()) {
                continue;
            }
            Path target = context.files().path(Stage.ASSETS, shot.id());
            finder.find(pick, shot.duration() + 0.5, video.width(), video.height(), used, target)
                    .ifPresent(media -> found.put(shot.id(), media));
        }
        log.info("Run {}: {} of {} real media requests found", context.run().id(), found.size(), picks.size());
        return verified(context, shots, picks, found);
    }

    public record Keep(List<String> keep) {
    }

    /** One cheap LLM pass rejects real media that does not show what the narration is about (same-name places...). */
    private Map<String, RealMediaFinder.Found> verified(StageContext context, ShotList shots,
                                                        Map<String, RealMedia> picks,
                                                        Map<String, RealMediaFinder.Found> found) {
        if (found.isEmpty()) {
            return found;
        }
        Alignment alignment = context.files().read(Stage.ALIGN, "alignment.json", Alignment.class);
        StringBuilder items = new StringBuilder();
        for (Shot shot : shots.shots()) {
            RealMediaFinder.Found media = found.get(shot.id());
            if (media != null) {
                items.append(String.join(" | ", shot.id(), RevisionService.spoken(alignment, shot),
                        picks.get(shot.id()).query(), media.label())).append('\n');
            }
        }
        try {
            Keep keep = llm.json(context, Stage.ASSETS, "verify_media", Map.of("topic", context.run().topicTitle(),
                    "items", items.toString()), Keep.class);
            Set<String> kept = keep.keep() == null ? Set.of() : Set.copyOf(keep.keep());
            Map<String, RealMediaFinder.Found> out = new HashMap<>();
            found.forEach((id, media) -> {
                if (kept.contains(id)) {
                    out.put(id, media);
                } else {
                    log.info("Run {} {}: real media rejected as off-topic: {}", context.run().id(), id, media.label());
                }
            });
            return out;
        } catch (LlmException e) {
            log.warn("Run {}: real media check failed, using illustrations instead: {}", context.run().id(),
                    e.getMessage());
            return Map.of();
        }
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
        Map<String, RealMediaFinder.Found> real = realMedia(context, shots);
        Set<String> allowedNew = budget(context, shots, style, real.keySet());
        for (Shot shot : shots.shots()) {
            RealMediaFinder.Found found = real.get(shot.id());
            if (found != null) {
                context.usage().provider(found.provider());
                assets.add(new Asset(shot.id(), context.files().relative(found.file()), shot.type(), found.provider(),
                        "real", found.key(), shot.seed(), BigDecimal.ZERO, false, found.license(), found.media(),
                        found.credit()));
                continue;
            }
            String name = shot.id() + ".png";
            Path file = context.files().path(Stage.ASSETS, name);
            String relative = context.files().relative(file);
            if (shot.graphic()) {
                CardRenderer.kineticText(file, video.width(), video.height(), shot.seed(), shot.text());
                context.usage().provider(GRAPHICS);
                assets.add(new Asset(shot.id(), relative, shot.type(), GRAPHICS, "java2d", null, shot.seed(),
                        BigDecimal.ZERO, false, LicenseRecord.generated(GRAPHICS), "ai", null));
                continue;
            }
            ImageResult result;
            ImageRequest request = request(shot, style);
            try {
                if (!allowedNew.contains(shot.id()) && !images.isCached(request)) {
                    throw new IllegalStateException("over today's free image budget, reusing on purpose");
                }
                result = images.generate(request, file);
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
                    shot.seed(), result.costUsd(), result.realistic(), result.license(), "ai", null));
        }
        if (fallbacks > 0) {
            log.warn("Run {}: {} of {} images are placeholders", context.run().id(), fallbacks, shots.shots().size());
        }
        boolean realistic = assets.stream().anyMatch(Asset::realistic);
        context.files().write(Stage.ASSETS, "assets.json", new AssetManifest(realistic, assets));
    }
}
