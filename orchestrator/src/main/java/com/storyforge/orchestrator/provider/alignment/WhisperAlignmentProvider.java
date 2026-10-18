package com.storyforge.orchestrator.provider.alignment;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "alignment", havingValue = "whisper")
public class WhisperAlignmentProvider implements AlignmentProvider {

    private final MediaSvcClient media;

    public WhisperAlignmentProvider(MediaSvcClient media) {
        this.media = media;
    }

    @Override
    public String name() {
        return "faster-whisper";
    }

    @Override
    public Alignment align(List<Scene> scenes, NarrationManifest narration, Path narrationWav) {
        Path runRoot = narrationWav.getParent().getParent();
        Map<String, ClipAlignment> clips = new HashMap<>();
        for (Scene scene : scenes) {
            SceneAudio clip = narration.scenes().stream().filter(a -> a.sceneId().equals(scene.id())).findFirst()
                    .orElseThrow(() -> new IllegalStateException("No narration clip for scene " + scene.id()));
            clips.put(scene.id(), media.align(runRoot.resolve(clip.file()), String.join(" ", scene.words())));
        }
        return AlignmentAssembler.assemble(name(), scenes, narration, clips);
    }

    @Override
    public Optional<ClipAlignment> alignClip(Path wav, String text) {
        return Optional.of(media.align(wav, text));
    }
}
