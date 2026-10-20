package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.model.MapPin;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.provider.clips.FilmClipFinder;
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
import java.util.Optional;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

@Component
public class AssetsStage implements StageWorker {

    private static final String GRAPHICS = "storyforge-graphics";
    private static final LicenseRecord MAP_LICENSE = new LicenseRecord("public_domain",
            "Natural Earth (public domain) + Wikidata coordinates (CC0)", "https://www.naturalearthdata.com/about/terms-of-use/",
            "Map drawn by StoryForge from Natural Earth data (public domain) and Wikidata coordinates (CC0)", null);
    private static final Logger log = LoggerFactory.getLogger(AssetsStage.class);

    private final ImageProvider images;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Video video;

    private final RealMediaFinder finder;
    private final LlmRouter llm;
    private final FilmClipFinder clipFinder;
    private final PreferencesService preferences;

    public AssetsStage(ImageProvider images, FormatRegistry formats, StoryForgeProperties properties,
                       RealMediaFinder finder, LlmRouter llm, FilmClipFinder clipFinder,
                       PreferencesService preferences) {
        this.finder = finder;
        this.llm = llm;
        this.clipFinder = clipFinder;
        this.preferences = preferences;
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

    public record StockQuery(String shot, String query) {
    }

    public record StockQueries(List<StockQuery> queries) {
    }

    /**
     * Pictures that could not be generated: one LLM call proposes stock searches where a stock photo can honestly
     * fit, the verify pass rejects anything off-topic, and the rest reuse a picture from the same scene or, as a
     * last resort, the branded backdrop. Never a text placeholder.
     */
    private void fillMissing(StageContext context, ShotList shots, FormatDefinition.Style style, List<Shot> missing,
                             List<Asset> assets, Set<String> used) {
        Map<String, RealMediaFinder.Found> stock = new HashMap<>();
        Map<String, RealMedia> queries = new HashMap<>();
        if (finder.stockAvailable()) {
            Alignment alignment = context.files().read(Stage.ALIGN, "alignment.json", Alignment.class);
            String items = missing.stream().map(shot -> String.join(" | ", shot.id(),
                    RevisionService.spoken(alignment, shot), description(shot, style)))
                    .collect(java.util.stream.Collectors.joining("\n"));
            try {
                StockQueries proposed = llm.json(context, Stage.ASSETS, "stock_queries",
                        Map.of("topic", context.run().topicTitle(), "items", items), StockQueries.class);
                if (proposed.queries() != null) {
                    proposed.queries().stream().filter(q -> q.shot() != null && q.query() != null && !q.query().isBlank())
                            .forEach(q -> queries.put(q.shot(), new RealMedia(RealMedia.PHOTO, q.query().strip())));
                }
            } catch (LlmException e) {
                log.warn("Run {}: stock queries failed: {}", context.run().id(), e.getMessage());
            }
            for (Shot shot : missing) {
                RealMedia query = queries.get(shot.id());
                if (query != null) {
                    finder.stockPhoto(query.query(), used, context.files().path(Stage.ASSETS, shot.id()))
                            .ifPresent(photo -> stock.put(shot.id(), photo));
                }
            }
        }
        Map<String, RealMediaFinder.Found> kept = verified(context, shots, queries, stock);
        Map<String, String> sceneOf = new HashMap<>();
        shots.shots().forEach(shot -> sceneOf.put(shot.id(), shot.sceneId()));
        for (Shot shot : missing) {
            RealMediaFinder.Found photo = kept.get(shot.id());
            if (photo != null) {
                context.usage().provider("stock-fallback");
                assets.add(new Asset(shot.id(), context.files().relative(photo.file()), shot.type(), "stock-fallback",
                        "pixabay", photo.label(), shot.seed(), BigDecimal.ZERO, false, photo.license(), "photo",
                        photo.credit()));
                continue;
            }
            Path file = context.files().path(Stage.ASSETS, shot.id() + ".png");
            ImageResult result = reuseOrBackdrop(context, shot, file, assets, sceneOf);
            context.usage().provider(result.provider());
            assets.add(new Asset(shot.id(), context.files().relative(file), shot.type(), result.provider(),
                    result.model(), shot.prompt(), shot.seed(), BigDecimal.ZERO, result.realistic(), result.license(),
                    "ai", null));
        }
        log.info("Run {}: {} stock photo(s) kept of {} missing pictures", context.run().id(), kept.size(),
                missing.size());
    }

    /** Reuses an AI picture from the same scene (else the nearest one), or draws the branded backdrop. */
    private ImageResult reuseOrBackdrop(StageContext context, Shot shot, Path file, List<Asset> assets,
                                        Map<String, String> sceneOf) {
        Asset donor = null;
        for (Asset candidate : assets) {
            boolean usable = "ai".equals(candidate.media()) && !candidate.provider().endsWith("-fallback")
                    && !GRAPHICS.equals(candidate.provider());
            if (usable && (donor == null || shot.sceneId().equals(sceneOf.get(candidate.shotId())))) {
                donor = candidate;
            }
        }
        if (donor != null) {
            try {
                Files.copy(context.files().root().resolve(donor.file()), file, StandardCopyOption.REPLACE_EXISTING);
                return new ImageResult("reused-fallback", donor.model(), BigDecimal.ZERO, donor.realistic(),
                        donor.license());
            } catch (IOException e) {
                log.warn("Run {} {}: reuse failed: {}", context.run().id(), shot.id(), e.getMessage());
            }
        }
        CardRenderer.backdrop(file, video.width(), video.height(), shot.seed());
        return new ImageResult("backdrop-fallback", "java2d", BigDecimal.ZERO, false,
                LicenseRecord.generated("storyforge-backdrop"));
    }

    /** The director's picture description without the style words, for stock photo searches. */
    private static String description(Shot shot, FormatDefinition.Style style) {
        String prompt = shot.prompt() == null ? "" : shot.prompt();
        String prefix = style.promptPrefix() + ", ";
        return prompt.startsWith(prefix) ? prompt.substring(prefix.length()) : prompt;
    }

    /** Fair-use trailer clips when the story is about a screen work and the owner has the mode on. */
    private Map<String, FilmClipFinder.Clip> clips(StageContext context, ShotList shots, Set<String> taken) {
        if (!clipFinder.enabled() || !preferences.get().fairUseClipsEnabled()) {
            return Map.of();
        }
        List<Scene> scenes = context.files().read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = context.files().read(Stage.ALIGN, "alignment.json", Alignment.class);
        return clipFinder.find(context, shots, alignment, scenes, taken, video.width(), video.height());
    }

    @Override
    public void execute(StageContext context) {
        ShotList shots = context.files().read(Stage.VISUAL_PLAN, "shotlist.json", ShotList.class);
        FormatDefinition.Style style = formats.get(context.run().format()).style();
        List<Asset> assets = new ArrayList<>();
        Map<String, RealMediaFinder.Found> real = realMedia(context, shots);
        Map<String, MapPin> maps = context.files().exists(Stage.VISUAL_PLAN, VisualPlanStage.MAPS)
                ? context.files().read(Stage.VISUAL_PLAN, VisualPlanStage.MAPS, new TypeReference<Map<String, MapPin>>() { })
                : Map.of();
        Set<String> taken = new HashSet<>(real.keySet());
        taken.addAll(maps.keySet());
        Map<String, FilmClipFinder.Clip> clips = clips(context, shots, taken);
        Set<String> notAi = new HashSet<>(taken);
        notAi.addAll(clips.keySet());
        Set<String> allowedNew = budget(context, shots, style, notAi);
        Set<String> stockUsed = new HashSet<>(real.values().stream().map(RealMediaFinder.Found::key).toList());
        List<Shot> missing = new ArrayList<>();
        for (Shot shot : shots.shots()) {
            MapPin pin = maps.get(shot.id());
            if (pin != null) {
                // The globe is drawn by the renderer; the file is only a placeholder for tools that need a picture.
                Path file = context.files().path(Stage.ASSETS, shot.id() + ".png");
                CardRenderer.placeholder(file, video.width(), video.height(), shot.seed(), "map", pin.label());
                assets.add(new Asset(shot.id(), context.files().relative(file), shot.type(), "globe-map", "d3-geo",
                        pin.label(), shot.seed(), BigDecimal.ZERO, false, MAP_LICENSE, "map",
                        "Map: Natural Earth · Wikidata"));
                continue;
            }
            FilmClipFinder.Clip clip = clips.get(shot.id());
            if (clip != null) {
                context.usage().provider("fair-use-clip");
                assets.add(new Asset(shot.id(), context.files().relative(clip.file()), shot.type(), "official-trailer",
                        "clip", clip.label(), shot.seed(), BigDecimal.ZERO, false, clip.license(), "clip",
                        clip.credit()));
                continue;
            }
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
                // No AI picture (free quota used up or provider down): handled together after the loop.
                missing.add(shot);
                continue;
            }
            if (result.costUsd().signum() > 0) {
                context.usage().call(result.provider(), 0, 0, result.costUsd());
            } else {
                context.usage().provider(result.provider());
            }
            assets.add(new Asset(shot.id(), relative, shot.type(), result.provider(), result.model(), shot.prompt(),
                    shot.seed(), result.costUsd(), result.realistic(), result.license(), "ai", null));
        }
        if (!missing.isEmpty()) {
            log.warn("Run {}: {} of {} pictures could not be generated; finding stock photos or reusing pictures",
                    context.run().id(), missing.size(), shots.shots().size());
            fillMissing(context, shots, style, missing, assets, stockUsed);
        }
        boolean realistic = assets.stream().anyMatch(Asset::realistic);
        context.files().write(Stage.ASSETS, "assets.json", new AssetManifest(realistic, assets));
    }
}
