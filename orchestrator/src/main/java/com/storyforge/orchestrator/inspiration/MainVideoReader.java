package com.storyforge.orchestrator.inspiration;

import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * The run's main inspiration video with what was said when and its "most replayed" curve. A transcript read before
 * those were kept is read again once (captions take seconds) and saved back; a Whisper transcript with timings is not
 * transcribed again only for the curve.
 */
@Component
public class MainVideoReader {

    /** The main video's link and its transcript (timed when YouTube or Whisper gave times). */
    public record MainVideo(String url, VideoTranscript transcript) {
    }

    private static final Logger log = LoggerFactory.getLogger(MainVideoReader.class);

    private final InspirationService inspirations;
    private final InspirationFiles files;
    private final MediaSvcClient media;

    public MainVideoReader(InspirationService inspirations, InspirationFiles files, MediaSvcClient media) {
        this.inspirations = inspirations;
        this.files = files;
        this.media = media;
    }

    public Optional<MainVideo> read(Long inspirationId) {
        if (inspirationId == null) {
            return Optional.empty();
        }
        try {
            String url = inspirations.get(inspirationId).getMainUrl();
            if (url == null || url.isBlank()) {
                return Optional.empty();
            }
            List<VideoTranscript> all = new ArrayList<>(files.transcripts(inspirationId));
            VideoTranscript transcript = all.stream().filter(t -> url.equals(t.url())).findFirst().orElse(null);
            boolean whisper = transcript != null && "whisper".equals(transcript.source());
            if (transcript == null || !transcript.timed() || transcript.heatmap() == null && !whisper) {
                VideoTranscript fresh = media.transcript(url);
                all.removeIf(t -> url.equals(t.url()));
                all.add(0, fresh);
                files.saveTranscripts(inspirationId, all);
                transcript = fresh;
            }
            return Optional.of(new MainVideo(url, transcript));
        } catch (RuntimeException e) {
            log.warn("Main video of inspiration {} not read: {}", inspirationId, e.getMessage());
            return Optional.empty();
        }
    }
}
