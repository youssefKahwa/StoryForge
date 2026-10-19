package com.storyforge.orchestrator.revision;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.artifact.RunOverrides;
import com.storyforge.orchestrator.format.FormatDefinition;
import com.storyforge.orchestrator.format.FormatRegistry;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Publication;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.model.VisualBible;
import com.storyforge.orchestrator.provider.llm.LlmException;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.run.Run;
import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.run.RunStatus;
import com.storyforge.orchestrator.script.SfmParser;
import com.storyforge.orchestrator.sound.MusicLibrary;
import com.storyforge.orchestrator.sound.SoundDesigner;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageUsage;
import com.storyforge.orchestrator.stage.worker.AudioMixStage;
import com.storyforge.orchestrator.stage.worker.VisualPlanStage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies owner notes to a finished run: rewrites a scene, re-prompts chosen images, changes music, or steers the
 * title/thumbnail/description, then re-runs the pipeline from the earliest stage that needs it. Caches keep
 * everything else as it was (same voice clips, same images, same LLM answers).
 */
@Service
public class RevisionService {

    public static final List<String> MUSIC_CHOICES = musicChoices();
    public static final List<String> PACKAGE_TARGETS = List.of("title", "thumbnail", "description", "all");
    private static final Logger log = LoggerFactory.getLogger(RevisionService.class);
    private static final Pattern SHOT_KEY = Pattern.compile("[A-Za-z0-9_-]{1,20}#\\d{1,3}");
    private static final Pattern SCENE_HEADER = Pattern.compile("(?m)^## SCENE (\\S+)");
    private static final double QUIETER_DB = -4;
    private static final double LOUDER_DB = 3;

    public record PromptFix(String key, String prompt) {
    }

    public record PromptFixes(List<PromptFix> prompts) {
    }

    private final RevisionRepository repository;
    private final RunService runs;
    private final ArtifactStore artifacts;
    private final FormatRegistry formats;
    private final LlmRouter llm;
    private final JsonMapper json;

    public RevisionService(RevisionRepository repository, RunService runs, ArtifactStore artifacts,
                           FormatRegistry formats, LlmRouter llm, JsonMapper json) {
        this.repository = repository;
        this.runs = runs;
        this.artifacts = artifacts;
        this.formats = formats;
        this.llm = llm;
        this.json = json;
    }

    /** Lists the run's revisions and settles queued ones once the re-run has finished. */
    @Transactional
    public List<Revision> list(long runId) {
        Run run = runs.get(runId);
        List<Revision> revisions = repository.findByRunIdOrderByCreatedAtAsc(runId);
        Instant finished = run.getFinishedAt();
        for (Revision revision : revisions) {
            if (revision.getStatus() != RevisionStatus.QUEUED || finished == null
                    || finished.isBefore(revision.getUpdatedAt())) {
                continue;
            }
            if (run.getStatus() == RunStatus.COMPLETED) {
                revision.mark(RevisionStatus.APPLIED, revision.getResult());
            } else if (run.getStatus() == RunStatus.FAILED || run.getStatus() == RunStatus.BLOCKED) {
                revision.mark(RevisionStatus.FAILED, "Re-run stopped: " + run.getStatusReason());
            }
        }
        return revisions;
    }

    @Transactional
    public Revision add(long runId, RevisionKind kind, String target, Double atSec, String note) {
        runs.get(runId);
        String cleanNote = note == null ? "" : note.strip();
        String cleanTarget = target == null ? "" : target.strip();
        switch (kind) {
            case IMAGE -> require(SHOT_KEY.matcher(cleanTarget).matches(), "Pick an image on the timeline");
            case NARRATION -> require(scenes(artifacts.forRun(runId)).stream()
                    .anyMatch(s -> s.id().equals(cleanTarget)), "Unknown scene " + cleanTarget);
            case MUSIC -> {
                require(cleanTarget.equals("all") || scenes(artifacts.forRun(runId)).stream()
                        .anyMatch(s -> s.id().equals(cleanTarget)), "Unknown scene " + cleanTarget);
                require(MUSIC_CHOICES.contains(cleanNote), "Music choice must be one of " + MUSIC_CHOICES);
            }
            case PACKAGE -> require(PACKAGE_TARGETS.contains(cleanTarget), "Target must be one of "
                    + PACKAGE_TARGETS);
        }
        require(!cleanNote.isEmpty(), "Write what should change");
        return repository.save(new Revision(runId, kind, cleanTarget, atSec, cleanNote));
    }

    @Transactional
    public void delete(long runId, long revisionId) {
        Revision revision = repository.findById(revisionId).filter(r -> r.getRunId() == runId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown revision " + revisionId));
        require(revision.getStatus() == RevisionStatus.PENDING || revision.getStatus() == RevisionStatus.FAILED,
                "Only pending or failed revisions can be removed");
        repository.delete(revision);
    }

    /** Applies every pending note and queues the re-run. LLM calls happen here, so this can take a minute. */
    public Run apply(long runId) {
        Run run = runs.get(runId);
        if (run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.QUEUED) {
            throw new IllegalStateException("Run " + runId + " is still working; wait until it finishes");
        }
        RunFiles files = artifacts.forRun(runId);
        refuseIfLive(files);
        List<Revision> pending = repository.findByRunIdAndStatus(runId, RevisionStatus.PENDING);
        if (pending.isEmpty()) {
            throw new IllegalArgumentException("No pending revisions to apply");
        }
        StageUsage usage = new StageUsage();
        int salt = run.getCacheSalt();
        RunOverrides overrides = files.overrides();
        Map<RevisionKind, List<Revision>> byKind = pending.stream()
                .collect(Collectors.groupingBy(Revision::getKind, LinkedHashMap::new, Collectors.toList()));
        try {
            if (byKind.containsKey(RevisionKind.NARRATION)) {
                reviseNarration(run, files, byKind.get(RevisionKind.NARRATION), usage, salt);
            }
            if (byKind.containsKey(RevisionKind.IMAGE)) {
                overrides = reviseImages(run, files, overrides, byKind.get(RevisionKind.IMAGE), usage, salt);
            }
            for (Revision revision : byKind.getOrDefault(RevisionKind.MUSIC, List.of())) {
                overrides = music(overrides, files, revision);
            }
            for (Revision revision : byKind.getOrDefault(RevisionKind.PACKAGE, List.of())) {
                overrides = overrides.withPackageNote("[" + revision.getTarget() + "] " + revision.getNote());
            }
        } catch (RuntimeException e) {
            pending.forEach(r -> {
                r.mark(RevisionStatus.FAILED, e.getMessage());
                repository.save(r);
            });
            throw e instanceof IllegalArgumentException ? e : new IllegalStateException(
                    "Could not apply the revisions: " + e.getMessage(), e);
        }
        files.writeOverrides(overrides);
        Stage from = pending.stream().map(r -> r.getKind().rerunFrom())
                .min(Comparator.comparingInt(Stage::ordinal)).orElseThrow();
        pending.forEach(r -> {
            r.mark(RevisionStatus.QUEUED, "Re-running from " + from.folder());
            repository.save(r);
        });
        log.info("Run {}: applied {} revision(s), re-running from {}", runId, pending.size(), from.folder());
        return runs.rerunFrom(runId, from, false);
    }

    private void reviseNarration(Run run, RunFiles files, List<Revision> revisions, StageUsage usage, int salt) {
        String markup = files.readText(Stage.CRITIQUE, "script.sfm.md");
        List<Scene> before = scenes(files);
        Dossier dossier = files.read(Stage.RESEARCH, "dossier.json", Dossier.class);
        for (Revision revision : revisions) {
            String id = revision.getTarget();
            int index = indexOf(before, id);
            Scene scene = before.get(index);
            Set<String> ids = scene.claims() == null ? Set.of() : Set.copyOf(scene.claims());
            List<Dossier.Claim> claims = dossier.claims().stream()
                    .filter(c -> ids.isEmpty() || ids.contains(c.id())).toList();
            String block = block(markup, id);
            String revised = stripFences(llm.text(usage, salt, Stage.CRITIQUE, "revise_scene", Map.of(
                    "topic", run.getTopicTitle(),
                    "note", revision.getNote(),
                    "scene", block,
                    "scene_id", id,
                    "before", index > 0 ? lastWords(before.get(index - 1).narration()) : "(this is the opening)",
                    "after", index < before.size() - 1 ? firstWords(before.get(index + 1).narration()) : "(this is the end)",
                    "claims", json.writeValueAsString(claims))));
            if (!revised.startsWith("## SCENE " + id)) {
                throw new IllegalStateException("The rewrite of scene " + id + " lost its header");
            }
            markup = markup.replace(block, revised.strip() + "\n\n");
        }
        List<Scene> after = SfmParser.parse(markup);
        if (!after.stream().map(Scene::id).toList().equals(before.stream().map(Scene::id).toList())) {
            throw new IllegalStateException("The rewrite changed the scene list; nothing was applied");
        }
        String stamp = String.valueOf(System.currentTimeMillis());
        files.writeRoot("history/scenes-" + stamp + ".json", before);
        files.writeText(Stage.CRITIQUE, "script.sfm.md", markup);
        files.write(Stage.CRITIQUE, "scenes.json", after);
    }

    private RunOverrides reviseImages(Run run, RunFiles files, RunOverrides overrides, List<Revision> revisions,
                                      StageUsage usage, int salt) {
        ShotList shots = files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class);
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        FormatDefinition.Style style = formats.get(run.getFormat()).style();
        Map<String, Shot> byKey = byKey(shots);
        VisualBible bible = files.exists(Stage.VISUAL_PLAN, VisualPlanStage.BIBLE)
                ? files.read(Stage.VISUAL_PLAN, VisualPlanStage.BIBLE, VisualBible.class) : VisualBible.empty();
        Map<String, String> current = new LinkedHashMap<>();
        StringBuilder items = new StringBuilder();
        for (Revision revision : revisions) {
            Shot shot = byKey.get(revision.getTarget());
            if (shot == null || shot.graphic()) {
                throw new IllegalArgumentException("Image " + revision.getTarget() + " is not a picture shot");
            }
            String prompt = stripPrefix(shot.prompt(), style.promptPrefix());
            current.put(revision.getTarget(), prompt);
            items.append(String.join(" | ", revision.getTarget(), spoken(alignment, shot), prompt,
                    revision.getNote())).append('\n');
        }
        Map<String, String> fixed = new HashMap<>();
        try {
            PromptFixes fixes = llm.json(usage, salt, Stage.VISUAL_PLAN, "revise_images",
                    Map.of("bible", json.writeValueAsString(bible), "items", items.toString()), PromptFixes.class);
            if (fixes.prompts() != null) {
                fixes.prompts().stream().filter(f -> f.key() != null && f.prompt() != null && !f.prompt().isBlank())
                        .forEach(f -> fixed.put(f.key(), f.prompt().strip()));
            }
        } catch (LlmException e) {
            log.warn("Run {}: image prompt revision failed, appending the notes instead: {}", run.getId(),
                    e.getMessage());
        }
        RunOverrides next = overrides;
        for (Revision revision : revisions) {
            String key = revision.getTarget();
            String prompt = fixed.getOrDefault(key, current.get(key) + ", " + revision.getNote());
            RunOverrides.ShotOverride previous = next.shots().get(key);
            int attempt = previous == null ? 1 : previous.attempt() + 1;
            next = next.withShot(key, new RunOverrides.ShotOverride(prompt, attempt, revision.getNote()));
        }
        return next;
    }

    private RunOverrides music(RunOverrides overrides, RunFiles files, Revision revision) {
        String choice = revision.getNote();
        String target = revision.getTarget();
        return switch (choice) {
            case "quieter" -> overrides.withMusicGain(Math.max(-12, overrides.musicGainDb() + QUIETER_DB));
            case "louder" -> overrides.withMusicGain(Math.min(6, overrides.musicGainDb() + LOUDER_DB));
            case "off" -> overrides.withMusicGain(AudioMixStage.MUSIC_OFF_DB - 20);
            case "on" -> overrides.withMusicGain(0);
            default -> {
                RunOverrides next = overrides;
                List<String> sceneIds = target.equals("all") ? scenes(files).stream().map(Scene::id).toList()
                        : List.of(target);
                for (String id : sceneIds) {
                    next = next.withSceneMusic(id, choice);
                }
                yield next;
            }
        };
    }

    /** Refuses when the video is already live or scheduled on YouTube: veto it first, then revise. */
    private static void refuseIfLive(RunFiles files) {
        if (!files.exists(Stage.PUBLISH, "publication.json")) {
            return;
        }
        Publication publication = files.read(Stage.PUBLISH, "publication.json", Publication.class);
        if ("youtube".equals(publication.provider()) && publication.videoId() != null
                && !"private-vetoed".equals(publication.privacy())) {
            throw new IllegalStateException("This video is already uploaded to YouTube. Press Veto first, then "
                    + "apply the revisions (a corrected copy is uploaded).");
        }
    }

    public static Map<String, Shot> byKey(ShotList shots) {
        Map<String, Integer> perScene = new HashMap<>();
        Map<String, Shot> byKey = new LinkedHashMap<>();
        for (Shot shot : shots.shots()) {
            byKey.put(VisualPlanStage.shotKey(shot.sceneId(), perScene.merge(shot.sceneId(), 1, Integer::sum)), shot);
        }
        return byKey;
    }

    static String block(String markup, String sceneId) {
        Matcher matcher = SCENE_HEADER.matcher(markup);
        int start = -1;
        while (matcher.find()) {
            if (start >= 0) {
                return markup.substring(start, matcher.start());
            }
            if (matcher.group(1).equals(sceneId)) {
                start = matcher.start();
            }
        }
        if (start < 0) {
            throw new IllegalArgumentException("Scene " + sceneId + " not found in the script");
        }
        return markup.substring(start);
    }

    private List<Scene> scenes(RunFiles files) {
        return files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
    }

    private static int indexOf(List<Scene> scenes, String id) {
        for (int i = 0; i < scenes.size(); i++) {
            if (scenes.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown scene " + id);
    }

    public static String spoken(Alignment alignment, Shot shot) {
        return alignment.words().stream()
                .filter(w -> (w.start() + w.end()) / 2 >= shot.start() && (w.start() + w.end()) / 2 < shot.end())
                .map(Alignment.Word::text).collect(Collectors.joining(" "));
    }

    private static String stripPrefix(String prompt, String prefix) {
        String p = prompt == null ? "" : prompt;
        return prefix != null && p.startsWith(prefix + ", ") ? p.substring(prefix.length() + 2) : p;
    }

    static String stripFences(String text) {
        String t = text.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```\\s*$", "");
        }
        return t.strip();
    }

    private static String lastWords(String text) {
        String[] words = (text == null ? "" : text.strip()).split("\\s+");
        return String.join(" ", List.of(words).subList(Math.max(0, words.length - 20), words.length));
    }

    private static String firstWords(String text) {
        String[] words = (text == null ? "" : text.strip()).split("\\s+");
        return String.join(" ", List.of(words).subList(0, Math.min(words.length, 20)));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static List<String> musicChoices() {
        List<String> choices = new ArrayList<>(MusicLibrary.MOODS);
        choices.addAll(List.of(SoundDesigner.SILENCE, "quieter", "louder", "off", "on"));
        return List.copyOf(choices);
    }
}
