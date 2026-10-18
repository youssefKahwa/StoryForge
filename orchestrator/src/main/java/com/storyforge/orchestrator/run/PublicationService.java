package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.model.Publication;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.youtube.YouTubeClient;
import org.springframework.stereotype.Service;

@Service
public class PublicationService {

    private static final String FILE = "publication.json";

    private final ArtifactStore artifacts;
    private final YouTubeClient youtube;

    public PublicationService(ArtifactStore artifacts, YouTubeClient youtube) {
        this.artifacts = artifacts;
        this.youtube = youtube;
    }

    public Publication veto(long runId) {
        RunFiles files = artifacts.forRun(runId);
        if (!files.exists(Stage.PUBLISH, FILE)) {
            throw new IllegalStateException("Run " + runId + " has not been uploaded yet");
        }
        Publication publication = files.read(Stage.PUBLISH, FILE, Publication.class);
        if ("youtube".equals(publication.provider())) {
            youtube.keepPrivate(publication.videoId());
        }
        Publication vetoed = publication.vetoed();
        files.write(Stage.PUBLISH, FILE, vetoed);
        return vetoed;
    }
}
