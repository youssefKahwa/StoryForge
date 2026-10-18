package com.storyforge.orchestrator.stage.worker;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.AudioQa;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.PackageDraft;
import com.storyforge.orchestrator.model.PackageDraft.ThumbnailConcept;
import com.storyforge.orchestrator.model.PublishKit;
import com.storyforge.orchestrator.model.QaReport;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.TopicBrief;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import com.storyforge.orchestrator.packaging.DescriptionBuilder;
import com.storyforge.orchestrator.packaging.ShortsPlanner;
import com.storyforge.orchestrator.packaging.TitleScorer;
import com.storyforge.orchestrator.provider.image.ImageProvider;
import com.storyforge.orchestrator.provider.image.ImageRequest;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.provider.render.RenderProvider;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.StageWorker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class PackageStage implements StageWorker {

    private static final int TOP_TITLES = 3;
    private static final int MAX_THUMBNAIL_WORDS = 4;

    private static final int MAX_THUMBNAILS = 3;
    private static final Logger log = LoggerFactory.getLogger(PackageStage.class);

    private final LlmRouter llm;
    private final JsonMapper json;
    private final ImageProvider images;
    private final RenderProvider renderer;
    private final FormatRegistry formats;
    private final StoryForgeProperties.Shorts shortsConfig;

    public PackageStage(LlmRouter llm, JsonMapper json, ImageProvider images, RenderProvider renderer,
                        FormatRegistry formats, StoryForgeProperties properties) {
        this.shortsConfig = properties.shorts();
        this.llm = llm;
        this.json = json;
        this.images = images;
        this.renderer = renderer;
        this.formats = formats;
    }

    private static List<ThumbnailConcept> concepts(PackageDraft draft, TopicBrief brief) {
        List<ThumbnailConcept> concepts = new ArrayList<>();
        if (draft.thumbnails() != null) {
            draft.thumbnails().stream().filter(c -> c != null && c.text() != null && !c.text().isBlank())
                    .limit(MAX_THUMBNAILS).forEach(c -> concepts.add(new ThumbnailConcept(shorten(c.text()),
                            c.scene() == null ? brief.angle() : c.scene())));
        }
        if (concepts.isEmpty()) {
            String text = draft.thumbnailText() != null ? draft.thumbnailText() : brief.title();
            concepts.add(new ThumbnailConcept(shorten(text), brief.angle()));
        }
        return concepts;
    }

    private String thumbnail(StageContext context, ThumbnailConcept concept, FormatDefinition.Style style, int n) {
        var files = context.files();
        Path background = files.path(Stage.PACKAGE, "thumb_bg_" + n + ".png");
        try {
            images.generate(new ImageRequest(style.promptPrefix() + ", " + concept.scene()
                    + ", one dramatic focal subject on the right, strong contrast, empty dark area on the left",
                    style.negativePrompt(), context.run().id() * 100 + n, RenderProvider.THUMB_WIDTH,
                    RenderProvider.THUMB_HEIGHT, "thumbnail", "thumbnail " + n), background);
        } catch (RuntimeException e) {
            log.warn("Run {} thumbnail {} background failed, using a frame from the video: {}", context.run().id(),
                    n, e.getMessage());
            background = videoStill(context, n);
        }
        Path out = files.path(Stage.PACKAGE, "thumbnail_" + n + ".png");
        renderer.renderThumbnail(background, concept.text(), style.palette(), out);
        return files.relative(out);
    }

    private List<PublishKit.ShortClip> shorts(StageContext context, Edl edl, PackageDraft draft, TopicBrief brief,
                                              String longTitle) {
        if (!shortsConfig.enabled() || !renderer.supportsShorts()) {
            return List.of();
        }
        var files = context.files();
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        String audio = files.relative(files.path(Stage.AUDIO_MIX, AudioMixStage.MIX));
        List<ShortsPlanner.Moment> moments = ShortsPlanner.pick(scenes, alignment, shortsConfig.count(),
                shortsConfig.maxSeconds());
        List<PublishKit.ShortClip> clips = new ArrayList<>();
        for (int i = 0; i < moments.size(); i++) {
            ShortsPlanner.Moment moment = moments.get(i);
            String title = draft.shortTitles() != null && draft.shortTitles().size() > i
                    ? draft.shortTitles().get(i) : brief.title();
            Path out = files.path(Stage.PACKAGE, "short_" + (i + 1) + ".mp4");
            try {
                renderer.renderShort(ShortsPlanner.spec(moment, edl, alignment, title, audio), files.root(), out);
                clips.add(new PublishKit.ShortClip(files.relative(out), title + " #shorts",
                        title + "\n\nFull story: \"" + longTitle + "\" on the channel.\n#shorts",
                        moment.length() + ShortsPlanner.END_CARD_SECONDS));
            } catch (RuntimeException e) {
                log.warn("Run {} short {} failed: {}", context.run().id(), i + 1, e.getMessage());
            }
        }
        return clips;
    }

    private Path videoStill(StageContext context, int n) {
        var files = context.files();
        if (!files.exists(Stage.ASSETS, "assets.json")) {
            return null;
        }
        List<AssetManifest.Asset> candidates = files.read(Stage.ASSETS, "assets.json", AssetManifest.class).assets()
                .stream()
                .filter(asset -> asset.provider().equals(images.name()))
                .sorted(Comparator.comparing(asset -> "establishing".equals(asset.kind()) ? 0 : 1))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        return files.root().resolve(candidates.get(Math.min(candidates.size() - 1, (n - 1) * 3)).file());
    }

    private static List<String> musicCredits(AudioQa audio) {
        return audio.tracks().stream()
                .filter(track -> "music".equals(track.kind()) && track.license() != null)
                .filter(track -> track.license().type() != null && track.license().type().startsWith("cc"))
                .map(track -> track.license().evidence() != null ? track.license().evidence() : track.title())
                .distinct().toList();
    }

    private static String shorten(String text) {
        return Arrays.stream(text.strip().split("\\s+")).limit(MAX_THUMBNAIL_WORDS)
                .collect(Collectors.joining(" "));
    }

    @Override
    public Stage stage() {
        return Stage.PACKAGE;
    }

    @Override
    public List<String> outputs() {
        return List.of("package.json", "description.txt", "thumbnail.png");
    }

    @Override
    public void execute(StageContext context) throws IOException {
        var files = context.files();
        TopicBrief brief = files.read(Stage.SELECT, "topic_brief.json", TopicBrief.class);
        Edl edl = files.read(Stage.ASSEMBLE, "edl.json", Edl.class);
        Dossier dossier = files.read(Stage.RESEARCH, "dossier.json", Dossier.class);
        QaReport qa = files.read(Stage.QA, "qa_report.json", QaReport.class);
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });

        PackageDraft draft = llm.json(context, stage(), "package", Map.of(
                "topic", brief.title(),
                "angle", brief.angle(),
                "hook", scenes.get(0).narration(),
                "chapters", json.writeValueAsString(edl.chapters().stream().map(Edl.EdlChapter::title).toList())),
                PackageDraft.class);
        List<ScoredTitle> titles = TitleScorer.top(draft.titles(), brief.title(), TOP_TITLES);
        if (titles.isEmpty()) {
            throw new IllegalStateException("Packaging produced no usable titles");
        }
        List<ThumbnailConcept> concepts = concepts(draft, brief);
        FormatDefinition.Style style = formats.get(context.run().format()).style();
        List<String> rendered = new ArrayList<>();
        for (int i = 0; i < concepts.size(); i++) {
            rendered.add(thumbnail(context, concepts.get(i), style, i + 1));
        }
        Files.copy(files.root().resolve(rendered.get(0)), files.path(Stage.PACKAGE, "thumbnail.png"),
                StandardCopyOption.REPLACE_EXISTING);
        List<String> credits = files.exists(Stage.AUDIO_MIX, "audio_qa.json")
                ? musicCredits(files.read(Stage.AUDIO_MIX, "audio_qa.json", AudioQa.class)) : List.of();
        List<String> hashtags = draft.hashtags() == null ? List.of() : draft.hashtags();
        String description = DescriptionBuilder.build(draft.hookLine(), edl.chapters(), dossier.sources(), credits,
                hashtags);
        files.writeText(Stage.PACKAGE, "description.txt", description);
        List<PublishKit.ShortClip> shorts = shorts(context, edl, draft, brief, titles.get(0).title());
        files.write(Stage.PACKAGE, "package.json", new VideoPackage(titles.get(0).title(), titles, description,
                draft.tags() == null ? List.of() : draft.tags(), draft.pinnedComment(),
                Stage.PACKAGE.folder() + "/thumbnail.png", concepts.get(0).text(),
                rendered.subList(1, rendered.size()), DescriptionBuilder.youtubeChapters(edl.chapters()),
                qa.disclosureRequired(), hashtags, draft.communityPost(), shorts));
    }
}
