package com.storyforge.orchestrator.provider.publish;

import com.storyforge.orchestrator.model.Publication;
import java.nio.file.Files;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "publish", havingValue = "mock",
        matchIfMissing = true)
public class MockPublishProvider implements PublishProvider {

    @Override
    public String name() {
        return "mock-publish";
    }

    @Override
    public Publication publish(PublishRequest request) {
        if (!Files.isRegularFile(request.video())) {
            throw new IllegalStateException("Video file missing: " + request.video());
        }
        String videoId = "mock-" + request.runId();
        return new Publication(name(), videoId, "https://youtube.example/watch?v=" + videoId, "private",
                Instant.now(), request.publishAt(), request.disclosure());
    }
}
