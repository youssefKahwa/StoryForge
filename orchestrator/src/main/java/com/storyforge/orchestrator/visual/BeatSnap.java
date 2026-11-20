package com.storyforge.orchestrator.visual;

import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.sound.BeatGrid;
import java.util.ArrayList;
import java.util.List;

/**
 * Cuts on the beat: each cut between two shots of the same scene moves to the nearest music beat when one is close.
 * A cut between scenes stays put (the chapter card and the scene's first words start there).
 */
public final class BeatSnap {

    /** Farther than this from a beat, the cut stays on its word: the picture must follow what is said. */
    public static final double SHOT_TOLERANCE = 0.25;
    public static final double MIN_SHOT = 1.0;

    private BeatSnap() {
    }

    public static ShotList shots(ShotList list, List<Double> beats) {
        if (beats.isEmpty() || list.shots().size() < 2) {
            return list;
        }
        List<Shot> shots = new ArrayList<>(list.shots());
        for (int i = 0; i + 1 < shots.size(); i++) {
            Shot left = shots.get(i);
            Shot right = shots.get(i + 1);
            if (!left.sceneId().equals(right.sceneId()) || Math.abs(left.end() - right.start()) > 0.01) {
                continue;
            }
            Double beat = BeatGrid.nearest(beats, left.end(), SHOT_TOLERANCE);
            if (beat == null || beat - left.start() < MIN_SHOT || right.end() - beat < MIN_SHOT) {
                continue;
            }
            shots.set(i, left.withTimes(left.start(), beat));
            shots.set(i + 1, right.withTimes(beat, right.end()));
        }
        return new ShotList(list.durationSec(), shots);
    }

    /** Share of the cuts (shot starts after the first) that sit within {@code tolerance} of a beat. */
    public static double onBeat(List<Double> cuts, List<Double> beats, double tolerance) {
        if (cuts.isEmpty() || beats.isEmpty()) {
            return 0;
        }
        return cuts.stream().filter(cut -> BeatGrid.nearest(beats, cut, tolerance) != null).count()
                / (double) cuts.size();
    }
}
