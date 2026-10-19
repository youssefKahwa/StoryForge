package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.revision.RevisionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Everything the Revise tab needs to point at a part of the finished video. */
public record TimelineDto(double durationSec, List<ShotItem> shots, List<SceneItem> scenes, String title,
                          List<String> thumbnails, List<String> musicChoices, List<String> packageTargets) {

    public record ShotItem(String key, String id, String sceneId, String type, double start, double end,
                           String asset, String prompt, String spoken, boolean picture) {
    }

    public record SceneItem(String id, String beat, double start, double end, String narration, String mood) {
    }

    public static TimelineDto from(ShotList shots, Map<String, String> assets, List<Scene> scenes,
                                   Alignment alignment, VideoPackage pkg) {
        List<ShotItem> shotItems = new ArrayList<>();
        RevisionService.byKey(shots).forEach((key, shot) -> shotItems.add(new ShotItem(key, shot.id(),
                shot.sceneId(), shot.type(), shot.start(), shot.end(), assets.get(shot.id()), shot.prompt(),
                RevisionService.spoken(alignment, shot), !shot.graphic())));
        List<SceneItem> sceneItems = new ArrayList<>();
        for (Scene scene : scenes) {
            alignment.scenes().stream().filter(span -> span.sceneId().equals(scene.id())).findFirst()
                    .ifPresent(span -> sceneItems.add(new SceneItem(scene.id(), scene.beat(), span.start(),
                            span.end(), scene.narration(), scene.music() == null ? null : scene.music().mood())));
        }
        List<String> thumbnails = new ArrayList<>();
        if (pkg != null && pkg.thumbnail() != null) {
            thumbnails.add(pkg.thumbnail());
        }
        if (pkg != null && pkg.thumbnailAlternates() != null) {
            thumbnails.addAll(pkg.thumbnailAlternates());
        }
        return new TimelineDto(alignment.durationSec(), shotItems, sceneItems, pkg == null ? null : pkg.title(),
                thumbnails, RevisionService.MUSIC_CHOICES, RevisionService.PACKAGE_TARGETS);
    }
}
