package com.storyforge.orchestrator.edl;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlAudio;
import com.storyforge.orchestrator.model.Edl.EdlCaption;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.Outline;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EdlBuilder {

    public static final int VERSION = 2;

    public record Frame(int width, int height, int fps) {
    }

    private EdlBuilder() {
    }

    public static Edl build(Frame frame, List<Scene> scenes, Alignment alignment, ShotList shotList,
                            AssetManifest assets, Outline outline, List<EdlAudio> audio,
                            List<EdlCaption> captions, Edl.EdlStyle style) {
        List<EdlShot> shots = shotList.shots().stream().map(shot -> new EdlShot(
                shot.id(), shot.sceneId(), shot.type(), shot.start(), shot.end(),
                assets.forShot(shot.id()).map(AssetManifest.Asset::file)
                        .orElseThrow(() -> new IllegalStateException("No asset for " + shot.id())),
                shot.motion(), shot.text(), "cut")).toList();
        return new Edl(VERSION, frame.width(), frame.height(), frame.fps(), alignment.durationSec(), null,
                shots, audio, captions, chapters(scenes, alignment, outline), style);
    }

    public static List<EdlChapter> chapters(List<Scene> scenes, Alignment alignment, Outline outline) {
        Map<String, String> titles = new HashMap<>();
        outline.beats().forEach(beat -> {
            if (beat.chapterTitle() != null && !beat.chapterTitle().isBlank()) {
                titles.putIfAbsent(beat.beat(), beat.chapterTitle());
            }
        });
        Map<String, Double> starts = new HashMap<>();
        alignment.scenes().forEach(span -> starts.put(span.sceneId(), span.start()));

        List<EdlChapter> chapters = new ArrayList<>();
        String previousBeat = null;
        for (Scene scene : scenes) {
            String title = titles.get(scene.beat());
            if (title != null && !scene.beat().equals(previousBeat)
                    && chapters.stream().noneMatch(chapter -> chapter.title().equals(title))) {
                double start = chapters.isEmpty() ? 0 : starts.getOrDefault(scene.id(), 0.0);
                chapters.add(new EdlChapter(start, title));
            }
            previousBeat = scene.beat();
        }
        return chapters;
    }
}
