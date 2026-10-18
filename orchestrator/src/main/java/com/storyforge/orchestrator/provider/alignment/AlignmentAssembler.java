package com.storyforge.orchestrator.provider.alignment;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.ClipAlignment;
import com.storyforge.orchestrator.model.ClipAlignment.ClipWord;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AlignmentAssembler {

    private AlignmentAssembler() {
    }

    public static Alignment assemble(String provider, List<Scene> scenes, NarrationManifest narration,
                                     Map<String, ClipAlignment> clips) {
        Map<String, SceneAudio> audio = narration.scenes().stream()
                .collect(Collectors.toMap(SceneAudio::sceneId, Function.identity()));
        List<Word> words = new ArrayList<>();
        List<SceneSpan> spans = new ArrayList<>();
        for (Scene scene : scenes) {
            SceneAudio clip = audio.get(scene.id());
            ClipAlignment aligned = clips.get(scene.id());
            if (clip == null || aligned == null) {
                throw new IllegalStateException("No audio alignment for scene " + scene.id());
            }
            double sceneEnd = clip.offsetSec() + clip.durationSec();
            double speechStart = clip.offsetSec() + clip.silenceBeforeMs() / 1000.0;
            spans.add(new SceneSpan(scene.id(), round(clip.offsetSec()), round(sceneEnd)));
            for (ClipWord word : aligned.words()) {
                double start = Math.min(sceneEnd, speechStart + word.start());
                double end = Math.min(sceneEnd, Math.max(start, speechStart + word.end()));
                words.add(new Word(word.text(), round(start), round(end), scene.id()));
            }
        }
        return new Alignment(provider, narration.durationSec(), words, spans);
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
