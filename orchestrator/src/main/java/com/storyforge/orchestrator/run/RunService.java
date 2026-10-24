package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.api.NotFoundException;
import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.settings.Preferences;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.topic.SeedTopic;
import com.storyforge.orchestrator.topic.SeedTopicService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RunService {

    private static final int MAX_REASON = 2000;

    private final RunRepository repository;
    private final SeedTopicService seedTopics;
    private final ArtifactStore artifacts;
    private final StoryForgeProperties properties;
    private final PreferencesService preferences;
    private final String profile;

    public RunService(RunRepository repository, SeedTopicService seedTopics, ArtifactStore artifacts,
                      StoryForgeProperties properties, PreferencesService preferences, Environment environment) {
        this.preferences = preferences;
        this.repository = repository;
        this.seedTopics = seedTopics;
        this.artifacts = artifacts;
        this.properties = properties;
        String[] active = environment.getActiveProfiles();
        this.profile = active.length == 0 ? "default" : String.join(",", active);
    }

    @Transactional(readOnly = true)
    public List<Run> list() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Run get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Run " + id + " not found"));
    }

    @Transactional
    public Run create(CreateRunRequest request) {
        Run run = new Run();
        if (request.seedTopicId() != null) {
            SeedTopic seed = seedTopics.get(request.seedTopicId());
            run.setSeedTopicId(seed.getId());
            run.setTopicTitle(seed.getTitle());
            run.setFormat(seed.getFormat());
            run.setLanguage(seed.getLanguage());
            seedTopics.markUsed(seed.getId());
        } else if (request.topicTitle() != null && !request.topicTitle().isBlank()) {
            run.setTopicTitle(request.topicTitle().trim());
            run.setFormat(request.format() != null ? request.format()
                    : preferences.saved().map(Preferences::defaultFormat).orElse(properties.defaults().format()));
            run.setLanguage(request.language() != null ? request.language() : properties.defaults().language());
        } else {
            throw new IllegalArgumentException("Either seedTopicId or topicTitle is required");
        }
        run.setInspirationId(request.inspirationId());
        String seedNotes = request.seedTopicId() != null ? seedTopics.get(request.seedTopicId()).getNotes() : null;
        run.setAngle(blankToNull(request.angle()) != null ? blankToNull(request.angle()) : blankToNull(seedNotes));
        run.setVoice(blankToNull(request.voice()) != null ? blankToNull(request.voice())
                : preferences.saved().map(Preferences::defaultVoice).orElse(null));
        Instant now = Instant.now();
        int requested = request.targetMinutes() != null
                ? (int) Math.round(request.targetMinutes() * 60)
                : preferences.saved().map(p -> (int) Math.round(p.defaultMinutes() * 60))
                        .orElse(properties.defaults().targetSeconds());
        // Long-form videos are at least 8 minutes: below that YouTube allows no mid-roll ads.
        run.setTargetSeconds(Math.max(properties.defaults().minSeconds(), requested));
        run.setProfile(profile);
        run.setState(RunState.CREATED);
        run.setStatus(RunStatus.QUEUED);
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        return repository.save(run);
    }

    @Transactional
    public Optional<Long> claimNextQueued() {
        return repository.findFirstByStatusOrderByCreatedAtAsc(RunStatus.QUEUED).map(run -> {
            Instant now = Instant.now();
            run.setStatus(RunStatus.RUNNING);
            run.setStatusReason(null);
            run.setUpdatedAt(now);
            if (run.getStartedAt() == null) {
                run.setStartedAt(now);
            }
            return run.getId();
        });
    }

    @Transactional
    public void advance(long id, RunState state) {
        Run run = get(id);
        run.setState(state);
        run.setUpdatedAt(Instant.now());
    }

    @Transactional
    public void complete(long id) {
        finish(id, RunStatus.COMPLETED, null);
    }

    @Transactional
    public void fail(long id, Stage stage, String reason) {
        finish(id, RunStatus.FAILED, stage.folder() + ": " + reason);
    }

    @Transactional
    public void block(long id, Stage stage, String reason) {
        finish(id, RunStatus.BLOCKED, stage.folder() + ": " + reason);
    }

    @Transactional
    public void requeue(long id, String reason) {
        Run run = get(id);
        run.setStatus(RunStatus.QUEUED);
        run.setStatusReason(truncate(reason));
        run.setUpdatedAt(Instant.now());
    }

    @Transactional
    public Run cancel(long id) {
        Run run = get(id);
        if (run.getStatus() == RunStatus.COMPLETED) {
            throw new IllegalStateException("Run " + id + " is already completed");
        }
        run.setStatus(RunStatus.CANCELLED);
        run.setStatusReason("Cancelled by owner");
        run.setUpdatedAt(Instant.now());
        return run;
    }

    @Transactional
    public Run resume(long id) {
        Run run = get(id);
        if (!run.getStatus().isResumable()) {
            throw new IllegalStateException("Run " + id + " is " + run.getStatus() + " and cannot be resumed");
        }
        run.setStatus(RunStatus.QUEUED);
        run.setStatusReason(null);
        run.setFinishedAt(null);
        run.setUpdatedAt(Instant.now());
        return run;
    }

    @Transactional
    public Run rerunFrom(long id, Stage stage, boolean fresh) {
        Run run = get(id);
        if (run.getStatus() == RunStatus.RUNNING) {
            throw new IllegalStateException("Run " + id + " is running; cancel it first");
        }
        artifacts.delete(id, stage.andLater());
        run.setState(stage.from());
        run.setStatus(RunStatus.QUEUED);
        run.setStatusReason("Re-run from " + stage.folder() + (fresh ? " (fresh)" : ""));
        run.setFinishedAt(null);
        if (fresh) {
            run.setCacheSalt(run.getCacheSalt() + 1);
        }
        run.setUpdatedAt(Instant.now());
        return run;
    }

    @Transactional
    public int recoverInterrupted() {
        List<Run> running = repository.findByStatus(RunStatus.RUNNING);
        running.forEach(run -> {
            run.setStatus(RunStatus.QUEUED);
            run.setStatusReason("Recovered after restart");
            run.setUpdatedAt(Instant.now());
        });
        return running.size();
    }

    private void finish(long id, RunStatus status, String reason) {
        Run run = get(id);
        Instant now = Instant.now();
        run.setStatus(status);
        run.setStatusReason(truncate(reason));
        run.setUpdatedAt(now);
        run.setFinishedAt(now);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String truncate(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), MAX_REASON));
    }
}
