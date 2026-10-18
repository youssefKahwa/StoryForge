package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.model.PublishKit;
import com.storyforge.orchestrator.model.PublishKit.ShortClip;
import com.storyforge.orchestrator.model.ScheduleSlot;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.qa.QaService;
import com.storyforge.orchestrator.stage.Stage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PublishKitBuilder {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (zzz)",
            Locale.ENGLISH);
    private static final String CATEGORY = "Education";

    private final Path exportsRoot;

    public PublishKitBuilder(StoryForgeProperties properties) {
        this.exportsRoot = Path.of(properties.dataDir()).toAbsolutePath().resolve("exports");
    }

    public PublishKit build(RunFiles files, VideoPackage pkg, ScheduleSlot slot, List<ShortClip> shorts) {
        Path dir = exportsRoot.resolve(LocalDate.now() + "-" + slug(pkg.title()));
        List<String> thumbnails = new ArrayList<>();
        thumbnails.add(pkg.thumbnail());
        thumbnails.addAll(pkg.thumbnailAlternates() == null ? List.of() : pkg.thumbnailAlternates());
        try {
            Files.createDirectories(dir);
            link(files.path(Stage.ASSEMBLE, QaService.VIDEO), dir.resolve("video.mp4"));
            link(files.path(Stage.ASSEMBLE, "captions.srt"), dir.resolve("captions.srt"));
            for (int i = 0; i < thumbnails.size(); i++) {
                link(files.root().resolve(thumbnails.get(i)), dir.resolve("thumbnail_" + (i + 1) + ".png"));
            }
            for (int i = 0; i < shorts.size(); i++) {
                link(files.root().resolve(shorts.get(i).file()), dir.resolve("short_" + (i + 1) + ".mp4"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<String> tags = TagFitter.fit(pkg.tags());
        List<String> hashtags = pkg.hashtags() == null ? List.of() : pkg.hashtags();
        boolean altered = pkg.disclosureRequired();
        PublishKit kit = new PublishKit(
                pkg.title(),
                pkg.titles().stream().map(t -> t.title()).filter(t -> !t.equals(pkg.title())).toList(),
                pkg.description(),
                tags,
                String.join(", ", tags),
                hashtags,
                pkg.pinnedComment(),
                pkg.communityPost(),
                thumbnails,
                files.relative(files.path(Stage.ASSEMBLE, QaService.VIDEO)),
                files.relative(files.path(Stage.ASSEMBLE, "captions.srt")),
                slot.publishAt(),
                slot.publishAt().atZone(ZoneId.systemDefault()).format(WHEN),
                slot.publishAt().atZone(ZoneId.of(slot.zone())).format(WHEN),
                CATEGORY,
                "English",
                false,
                altered,
                altered ? "The video contains realistic AI-generated scenes; YouTube requires the disclosure."
                        : "Illustrations are clearly stylized and the narrator is not a real person, so the "
                        + "altered-content disclosure is not required. The description still states AI use.",
                checklist(altered, hashtags),
                shorts,
                dir.toString());
        write(dir.resolve("publish_kit.md"), markdown(kit));
        write(dir.resolve("description.txt"), kit.description());
        write(dir.resolve("tags.txt"), kit.tagsCsv());
        return kit;
    }

    static List<String> checklist(boolean altered, List<String> hashtags) {
        List<String> steps = new ArrayList<>(List.of(
                "Upload video.mp4 in YouTube Studio (Create > Upload videos).",
                "Paste the title and description (chapters, sources and AI note are already inside).",
                "Thumbnail: upload thumbnail_1.png (keep 2 and 3 for a later A/B test with Test & Compare).",
                "Audience: \"No, it's not made for kids\".",
                "Altered content: " + (altered ? "Yes." : "No."),
                "Show more > Tags: paste tags.txt. Language: English. Category: Education.",
                "Subtitles: upload captions.srt (with timing).",
                "End screen (last 20 s): Subscribe element + \"Best for viewer\" video.",
                "Visibility: Schedule for the time below, or Private first if you want to check it once more.",
                "After publishing: pin the comment below, and post the community post one day later.",
                "Shorts: upload each short_N.mp4 on the following days with its title; link the full video "
                        + "with the Related video field."));
        if (!hashtags.isEmpty()) {
            steps.add(2, "The description ends with " + String.join(" ", hashtags)
                    + "; YouTube shows the first three above the title.");
        }
        return steps;
    }

    static String markdown(PublishKit kit) {
        StringBuilder md = new StringBuilder();
        md.append("# Publish kit: ").append(kit.title()).append("\n\n");
        md.append("## Title\n").append(kit.title()).append("\n\nAlternatives:\n");
        kit.alternativeTitles().forEach(t -> md.append("- ").append(t).append('\n'));
        md.append("\n## Schedule\n").append("- Your time: ").append(kit.publishAtLocal()).append('\n')
                .append("- Audience time: ").append(kit.publishAtAudience()).append("\n\n");
        md.append("## Description\n```\n").append(kit.description()).append("\n```\n\n");
        md.append("## Tags\n```\n").append(kit.tagsCsv()).append("\n```\n\n");
        md.append("## Hashtags\n").append(String.join(" ", kit.hashtags())).append("\n\n");
        md.append("## Pinned comment\n").append(nullToDash(kit.pinnedComment())).append("\n\n");
        md.append("## Community post (one day after publishing)\n").append(nullToDash(kit.communityPost()))
                .append("\n\n");
        md.append("## YouTube Studio checklist\n");
        kit.studioChecklist().forEach(step -> md.append("- [ ] ").append(step).append('\n'));
        md.append("\nAltered content: ").append(kit.alteredContentReason()).append("\n\n");
        if (!kit.shorts().isEmpty()) {
            md.append("## Shorts\n");
            for (int i = 0; i < kit.shorts().size(); i++) {
                ShortClip clip = kit.shorts().get(i);
                md.append("### short_").append(i + 1).append(".mp4 (").append(Math.round(clip.seconds())).append(" s)\n")
                        .append("Title: ").append(clip.title()).append("\n\n").append(clip.description()).append("\n\n");
            }
        }
        return md.toString();
    }

    static String slug(String title) {
        String ascii = Normalizer.normalize(title == null ? "video" : title, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return ascii.isEmpty() ? "video" : ascii.substring(0, Math.min(ascii.length(), 60));
    }

    private static void link(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source)) {
            return;
        }
        Files.deleteIfExists(target);
        try {
            Files.createLink(target, source);
        } catch (IOException | UnsupportedOperationException e) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void write(Path file, String text) {
        try {
            Files.writeString(file, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
