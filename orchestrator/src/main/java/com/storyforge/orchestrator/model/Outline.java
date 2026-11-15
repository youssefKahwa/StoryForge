package com.storyforge.orchestrator.model;

import java.util.List;

/**
 * The plan of the script. With a main inspiration video the outline is a story spine: every beat is one moment told
 * as a scene, the question it raises, the turn into the next beat ("but" / "so"), one take, and the loop that pulls
 * the viewer on; {@code spine} holds the arc, the clues planted for the twist and three hook options.
 */
public record Outline(int totalSeconds, List<Beat> beats, Spine spine) {

    public Outline(int totalSeconds, List<Beat> beats) {
        this(totalSeconds, beats, null);
    }

    /**
     * {@code points}: what the beat must say, in order. moment, question, turn, take and loop are set on a story
     * spine (null otherwise).
     */
    public record Beat(String beat, String chapterTitle, int targetSeconds, int wordBudget, String summary,
                       List<String> points, String rehook, List<String> claimIds, String moment, String question,
                       String turn, String take, String loop) {

        public Beat(String beat, String chapterTitle, int targetSeconds, int wordBudget, String summary,
                    List<String> points, String rehook, List<String> claimIds) {
            this(beat, chapterTitle, targetSeconds, wordBudget, summary, points, rehook, claimIds, null, null, null,
                    null, null);
        }

        public Beat withBudget(int seconds, int words) {
            return new Beat(beat, chapterTitle, seconds, words, summary, points == null ? List.of() : points, rehook,
                    claimIds == null ? List.of() : claimIds, moment, question, turn, take, loop);
        }
    }

    /** arc: setup, inciting moment, escalation, twist, ending; plants: clue -> where it pays off; hooks: 3 options. */
    public record Spine(Arc arc, List<Plant> plants, List<String> hooks, Integer chosenHook) {
    }

    public record Arc(String setup, String incident, String escalation, String twist, String ending) {
    }

    public record Plant(String clue, String plantIn, String payoffIn) {
    }
}
