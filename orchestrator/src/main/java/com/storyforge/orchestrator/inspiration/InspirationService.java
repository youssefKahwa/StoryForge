package com.storyforge.orchestrator.inspiration;

import com.storyforge.orchestrator.api.NotFoundException;
import com.storyforge.orchestrator.dto.CreateInspirationRequest;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.llm.LlmRouter;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageUsage;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.springframework.context.ApplicationEventPublisher;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class InspirationService {

    public static final int MAX_VIDEOS = 3;
    private static final int MAX_TRANSCRIPT_CHARS = 30_000;
    private static final int MAX_ERROR = 4000;
    private static final Logger log = LoggerFactory.getLogger(InspirationService.class);

    private final InspirationRepository repository;
    private final InspirationFiles files;
    private final MediaSvcClient media;
    private final LlmRouter llm;
    private final JsonMapper json;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> new Thread(task, "inspiration"));

    private final ApplicationEventPublisher events;

    public InspirationService(InspirationRepository repository, InspirationFiles files, MediaSvcClient media,
                              LlmRouter llm, JsonMapper json, ApplicationEventPublisher events) {
        this.events = events;
        this.repository = repository;
        this.files = files;
        this.media = media;
        this.llm = llm;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Inspiration get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Inspiration " + id + " not found"));
    }

    @Transactional
    public Inspiration create(CreateInspirationRequest request) {
        return create(request, false, null);
    }

    /** With autoStart, a run starts by itself as soon as the analysis is ready (Quick start). */
    @Transactional
    public Inspiration create(CreateInspirationRequest request, boolean autoStart, String ownerNotes) {
        List<String> others = request.urls() == null ? List.of()
                : request.urls().stream().filter(url -> url != null && !url.isBlank()).map(String::strip).toList();
        boolean pasted = request.pastedText() != null && !request.pastedText().isBlank();
        if (others.size() > MAX_VIDEOS) {
            throw new IllegalArgumentException("At most " + MAX_VIDEOS + " videos");
        }
        // The main video comes first: its transcript leads the analysis and the script.
        String main = request.mainUrl() == null || request.mainUrl().isBlank() ? null : request.mainUrl().strip();
        List<String> urls = new ArrayList<>();
        if (main != null) {
            urls.add(main);
        }
        others.stream().filter(url -> !url.equals(main)).forEach(urls::add);
        if (urls.isEmpty() && !pasted) {
            throw new IllegalArgumentException("Give at least one YouTube link or paste a transcript");
        }
        Inspiration inspiration = new Inspiration();
        inspiration.setUrlList(urls);
        inspiration.setMainUrl(main);
        inspiration.setPastedText(pasted ? request.pastedText().strip() : null);
        inspiration.setStatus(InspirationStatus.PENDING);
        inspiration.setAutoStart(autoStart);
        inspiration.setOwnerNotes(ownerNotes == null || ownerNotes.isBlank() ? null : ownerNotes.strip());
        inspiration.setCreatedAt(Instant.now());
        Inspiration saved = repository.save(inspiration);
        // Start the analysis only once the new row is committed; otherwise the worker thread may not find it yet.
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            executor.submit(() -> process(saved.getId()));
                        }
                    });
        } else {
            executor.submit(() -> process(saved.getId()));
        }
        return saved;
    }

    public List<VideoTranscript> transcripts(long id) {
        return files.transcripts(id);
    }

    public Optional<InspirationIdeas> ideas(long id) {
        return files.ideas(id);
    }

    void process(long id) {
        try {
            Inspiration inspiration = get(id);
            List<VideoTranscript> transcripts = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            for (String url : inspiration.getUrlList()) {
                try {
                    transcripts.add(transcript(url));
                } catch (RuntimeException e) {
                    log.warn("Transcript failed for {}: {}", url, e.getMessage());
                    errors.add(url + ": " + e.getMessage());
                }
            }
            if (inspiration.getPastedText() != null) {
                transcripts.add(new VideoTranscript(null, null, "Pasted by owner", null, null, null, "pasted",
                        inspiration.getPastedText()));
            }
            if (transcripts.isEmpty()) {
                throw new IllegalStateException("No transcript could be read. Paste one instead. " + errors);
            }
            files.saveTranscripts(id, transcripts);
            String mainUrl = inspiration.getMainUrl();
            VideoTranscript main = mainUrl == null ? null
                    : transcripts.stream().filter(t -> mainUrl.equals(t.url())).findFirst().orElse(null);
            InspirationIdeas ideas = llm.json(new StageUsage(), (int) id, Stage.DISCOVER, "inspire",
                    Map.of("transcripts", json.writeValueAsString(transcripts.stream().map(t -> Map.of(
                            "title", String.valueOf(t.title()),
                            "main", t == main,
                            "language", String.valueOf(t.language()),
                            "transcript", truncate(t.transcript(), t == main ? MAX_TRANSCRIPT_CHARS * 2
                                    : MAX_TRANSCRIPT_CHARS))).toList()),
                            "main", main == null ? "(none)" : "\"" + main.title() + "\""),
                    InspirationIdeas.class);
            if (ideas.angles() == null || ideas.angles().isEmpty() || ideas.story() == null) {
                throw new IllegalStateException("Inspiration analysis returned no story or angles");
            }
            files.saveIdeas(id, ideas);
            finish(id, InspirationStatus.READY, errors.isEmpty() ? null : String.join("; ", errors));
            events.publishEvent(new InspirationFinished(id, InspirationStatus.READY, null));
        } catch (RuntimeException e) {
            log.error("Inspiration {} failed", id, e);
            finish(id, InspirationStatus.FAILED, e.getMessage());
            events.publishEvent(new InspirationFinished(id, InspirationStatus.FAILED, e.getMessage()));
        }
    }

    /** Reuses a transcript of the same video when there is one, else reads it (one retry: long CPU jobs can time out). */
    private VideoTranscript transcript(String url) {
        Optional<VideoTranscript> known = files.known(url);
        if (known.isPresent()) {
            log.info("Transcript of {} reused from an earlier inspiration", url);
            return known.get();
        }
        try {
            return media.transcript(url);
        } catch (RuntimeException first) {
            log.warn("Transcript failed for {} ({}), trying once more", url, first.getMessage());
            return media.transcript(url);
        }
    }

    @Transactional
    public void finish(long id, InspirationStatus status, String error) {
        Inspiration inspiration = repository.findById(id).orElseThrow();
        inspiration.setStatus(status);
        inspiration.setError(error == null ? null : error.substring(0, Math.min(error.length(), MAX_ERROR)));
        repository.save(inspiration);
    }

    private static String truncate(String text, int max) {
        return text == null ? "" : text.length() <= max ? text : text.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }
}
