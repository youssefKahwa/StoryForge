package com.storyforge.orchestrator.provider.alignment;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Alignment.SceneSpan;
import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.NarrationManifest;
import com.storyforge.orchestrator.model.NarrationManifest.SceneAudio;
import com.storyforge.orchestrator.model.Scene;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "alignment", havingValue = "mock",
        matchIfMissing = true)
public class MockAlignmentProvider implements AlignmentProvider {

    @Override
    public String name() {
        return "mock-alignment";
    }

    @Override
    public Alignment align(List<Scene> scenes, NarrationManifest narration, Path narrationWav) {
        Map<String, SceneAudio> audio = narration.scenes().stream()
                .collect(Collectors.toMap(SceneAudio::sceneId, Function.identity()));
        List<Word> words = new ArrayList<>();
        List<SceneSpan> spans = new ArrayList<>();
        for (Scene scene : scenes) {
            SceneAudio clip = audio.get(scene.id());
            double sceneStart = clip.offsetSec();
            double sceneEnd = clip.offsetSec() + clip.durationSec();
            spans.add(new SceneSpan(scene.id(), round(sceneStart), round(sceneEnd)));

            double speechStart = sceneStart + clip.silenceBeforeMs() / 1000.0;
            double speechEnd = sceneEnd - clip.silenceAfterMs() / 1000.0;
            List<String> tokens = scene.words();
            double totalWeight = tokens.stream().mapToDouble(MockAlignmentProvider::weight).sum();
            double cursor = speechStart;
            for (String token : tokens) {
                double slot = (speechEnd - speechStart) * weight(token) / totalWeight;
                words.add(new Word(token, round(cursor), round(cursor + slot * 0.85), scene.id()));
                cursor += slot;
            }
        }
        return new Alignment(name(), narration.durationSec(), words, spans);
    }

    private static double weight(String token) {
        return token.length() + 2.0;
    }

    private static double round(double seconds) {
        return Math.round(seconds * 1000) / 1000.0;
    }
}
