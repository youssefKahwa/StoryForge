package com.storyforge.orchestrator.provider.clips;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.provider.clips.FootageIndex.Segment;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Choice;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Need;
import com.storyforge.orchestrator.provider.clips.FootagePlanner.Pick;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FootagePlannerTest {

    private static final MediaSvcClient.Trailer A = new MediaSvcClient.Trailer(Path.of("a.mp4"), "a", "A", "c", "u", 100);
    private static final MediaSvcClient.Trailer B = new MediaSvcClient.Trailer(Path.of("b.mp4"), "b", "B", "c", "u", 100);

    /** Ten separate moments of 6 s in source A, none back to back. */
    private static List<Segment> library() {
        List<Segment> segments = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            segments.add(segment(i, A, i * 10.0, i * 10.0 + 6, i, 100 + i, 40, false));
        }
        return segments;
    }

    private static Segment segment(int tile, MediaSvcClient.Trailer source, double start, double end, int moment,
                                   int family, double brightness, boolean soft) {
        return new Segment(tile, source, start, end, moment, family, brightness, soft, true);
    }

    private static Need shot(String id, double start) {
        return new Need(id, start, start + 4);
    }

    private static List<Pick> picks(int... tiles) {
        List<Pick> picks = new ArrayList<>();
        for (int tile : tiles) {
            picks.add(new Pick(tile, null));
        }
        return picks;
    }

    private static Choice place(FootagePlanner planner, Need need, List<Pick> picks) {
        Choice choice = planner.choose(need, picks).orElseThrow();
        planner.commit(choice);
        return choice;
    }

    @Test
    void takesTheFirstPickWhenItIsFree() {
        Choice choice = place(new FootagePlanner(library(), 2), shot("s1", 0), picks(3, 4));
        assertThat(choice.moments()).containsExactly(3);
        assertThat(choice.seconds()).isCloseTo(4.6, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void anUnusedLowerPickBeatsASecondUseOfTheFirst() {
        FootagePlanner planner = new FootagePlanner(library(), 2);
        place(planner, shot("s1", 0), picks(3));
        assertThat(place(planner, shot("s2", 200), picks(3, 4)).moments()).containsExactly(4);
    }

    @Test
    void aMomentIsNeverUsedMoreThanTheLimit() {
        FootagePlanner planner = new FootagePlanner(library(), 2);
        place(planner, shot("s1", 0), picks(3));
        place(planner, shot("s2", 200), picks(3));
        assertThat(planner.choose(shot("s3", 400), picks(3))).isEmpty();
    }

    @Test
    void aLimitOfOneNeverRepeats() {
        FootagePlanner planner = new FootagePlanner(library(), 1);
        place(planner, shot("s1", 0), picks(3));
        assertThat(planner.choose(shot("s2", 400), picks(3))).isEmpty();
    }

    @Test
    void aSecondUseWaitsNinetySecondsInBothDirections() {
        FootagePlanner planner = new FootagePlanner(library(), 2);
        place(planner, shot("s1", 200), picks(3));
        assertThat(planner.choose(shot("next", 204), picks(3))).isEmpty();
        assertThat(planner.choose(shot("soon", 289), picks(3))).isEmpty();
        assertThat(planner.choose(shot("before", 111), picks(3))).isEmpty();
        assertThat(planner.choose(shot("far", 290), picks(3))).isPresent();
    }

    @Test
    void aSecondUseShowsALaterPartOfALongMoment() {
        List<Segment> long9 = List.of(segment(1, A, 10, 19, 1, 1, 40, false));
        FootagePlanner planner = new FootagePlanner(long9, 2);
        Choice first = place(planner, shot("s1", 0), picks(1));
        Choice second = place(planner, shot("s2", 200), picks(1));
        assertThat(first.parts().get(0).from()).isCloseTo(10.05, org.assertj.core.data.Offset.offset(0.01));
        assertThat(second.parts().get(0).from()).isGreaterThan(14);
        assertThat(second.parts().get(0).from() + second.parts().get(0).seconds()).isLessThanOrEqualTo(19);
    }

    @Test
    void extraMomentsCountAsUses() {
        List<Segment> short3 = List.of(
                segment(1, A, 10, 12.5, 1, 1, 40, false),
                segment(2, A, 20, 22.5, 2, 2, 40, false),
                segment(3, A, 30, 36, 3, 3, 40, false));
        FootagePlanner planner = new FootagePlanner(short3, 1);
        Choice choice = place(planner, shot("s1", 0), List.of(new Pick(1, List.of(2))));
        assertThat(choice.moments()).containsExactly(1, 2);
        assertThat(planner.used(2)).isTrue();
        assertThat(planner.choose(shot("s2", 400), picks(2))).isEmpty();
    }

    @Test
    void followOnMomentsCountAsUsesAndObeyTheLimit() {
        List<Segment> backToBack = List.of(
                segment(1, A, 10, 12.5, 1, 1, 40, false),
                segment(2, A, 12.5, 15.5, 2, 2, 40, false),
                segment(3, A, 15.5, 18, 3, 3, 40, false));
        FootagePlanner planner = new FootagePlanner(backToBack, 1);
        Choice choice = place(planner, shot("s1", 0), picks(1));
        assertThat(choice.moments()).containsExactly(1, 2);

        FootagePlanner used = new FootagePlanner(backToBack, 1);
        place(used, new Need("s0", 0, 1.5), picks(2));
        assertThat(used.choose(shot("s1", 400), picks(1))).isEmpty();
    }

    @Test
    void aChainThatCannotCoverTheShotIsNotUsed() {
        List<Segment> tooShort = List.of(segment(1, A, 10, 12, 1, 1, 40, false));
        assertThat(new FootagePlanner(tooShort, 2).choose(shot("s1", 0), picks(1))).isEmpty();
    }

    @Test
    void aSlightlyShortMomentIsPlayedSlowerInsteadOfRefused() {
        List<Segment> nearly = List.of(segment(1, A, 10, 14, 1, 1, 40, false));
        Optional<Choice> choice = new FootagePlanner(nearly, 2).choose(shot("s1", 0), picks(1));
        assertThat(choice).isPresent();
        assertThat(choice.get().seconds()).isCloseTo(3.9, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void theShotsOtherPicksFillAShortFirstPick() {
        List<Segment> shortFirst = List.of(
                segment(1, A, 10, 12, 1, 1, 40, false),
                segment(2, A, 20, 26, 2, 2, 40, false));
        Choice choice = new FootagePlanner(shortFirst, 2).choose(shot("s1", 0), picks(1, 2)).orElseThrow();
        assertThat(choice.moments()).containsExactly(1, 2);
    }

    @Test
    void shortPicksAreFilledWithOtherTakesOfTheSameSetUp() {
        List<Segment> takes = List.of(
                segment(1, A, 10, 12, 1, 7, 40, false),
                segment(2, B, 50, 52, 2, 9, 40, false),
                segment(3, A, 30, 32.5, 3, 7, 40, false),
                segment(4, A, 40, 42.5, 4, 7, 40, false),
                segment(5, B, 60, 69, 5, 8, 40, false));
        Choice choice = new FootagePlanner(takes, 2).choose(new Need("s1", 0, 7), picks(1, 2)).orElseThrow();
        assertThat(choice.moments()).containsExactly(1, 2, 3, 4);
        assertThat(choice.moments()).doesNotContain(5);
    }

    @Test
    void aLongShotIsAQuickMontageOfAtMostFiveMoments() {
        List<Segment> short2 = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            short2.add(segment(i, A, i * 10.0, i * 10.0 + 2, i, 7, 40, false));
        }
        Choice choice = new FootagePlanner(short2, 2).choose(new Need("s1", 0, 8.5), picks(1)).orElseThrow();
        assertThat(choice.moments()).hasSize(5);
        assertThat(new FootagePlanner(short2, 2).choose(new Need("s1", 0, 13), picks(1))).isEmpty();
    }

    @Test
    void cleanFootageBeatsADarkOrSoftFirstPick() {
        List<Segment> mixed = List.of(
                segment(1, A, 10, 16, 1, 1, 12, false),
                segment(2, A, 20, 26, 2, 2, 40, true),
                segment(3, A, 30, 36, 3, 3, 40, false));
        Choice choice = new FootagePlanner(mixed, 2).choose(shot("s1", 0), picks(1, 2, 3)).orElseThrow();
        assertThat(choice.moments()).containsExactly(3);
    }

    @Test
    void aDarkMomentIsStillBetterThanNothing() {
        List<Segment> dark = List.of(segment(1, A, 10, 16, 1, 1, 12, false));
        assertThat(new FootagePlanner(dark, 2).choose(shot("s1", 0), picks(1))).isPresent();
    }

    @Test
    void theSameSetUpBackToBackIsAvoidedWhenAnythingElseFits() {
        List<Segment> family = List.of(
                segment(1, A, 10, 16, 1, 7, 40, false),
                segment(2, A, 20, 26, 2, 7, 40, false),
                segment(3, A, 30, 36, 3, 8, 40, false));
        FootagePlanner planner = new FootagePlanner(family, 2);
        place(planner, shot("s1", 0), picks(1));
        assertThat(place(planner, shot("s2", 4), picks(2, 3)).moments()).containsExactly(3);
    }

    @Test
    void theSameSetUpBackToBackIsAcceptedWhenNothingElseFits() {
        List<Segment> family = List.of(
                segment(1, A, 10, 16, 1, 7, 40, false),
                segment(2, A, 20, 26, 2, 7, 40, false));
        FootagePlanner planner = new FootagePlanner(family, 2);
        place(planner, shot("s1", 0), picks(1));
        assertThat(place(planner, shot("s2", 4), picks(2)).moments()).containsExactly(2);
    }

    @Test
    void aRejectedClipMovesToTheNextPickAndItsMomentIsNotOfferedAgain() {
        FootagePlanner planner = new FootagePlanner(library(), 2);
        Need need = shot("s1", 0);
        Choice first = planner.choose(need, picks(3, 4)).orElseThrow();
        planner.reject(first);
        assertThat(planner.choose(need, picks(3, 4)).orElseThrow().moments()).containsExactly(4);
        assertThat(planner.choose(shot("s2", 400), picks(3))).isEmpty();
    }

    @Test
    void aRejectedChainBlocksOnlyForThisShotUnlessThePartIsDark() {
        List<Segment> chain = List.of(
                segment(1, A, 10, 12.5, 1, 1, 40, false),
                segment(2, A, 20, 22.5, 2, 2, 12, false),
                segment(3, A, 30, 36, 3, 3, 40, false));
        FootagePlanner planner = new FootagePlanner(chain, 2);
        Need need = shot("s1", 0);
        planner.reject(planner.choose(need, List.of(new Pick(1, List.of(2)))).orElseThrow());
        assertThat(planner.choose(need, picks(1))).isEmpty();
        assertThat(planner.choose(shot("s2", 400), picks(1, 3)).orElseThrow().moments()).contains(1);
        assertThat(planner.choose(shot("s3", 400), picks(2))).isEmpty();
    }

    @Test
    void aMissingOrUnknownTileIsIgnored() {
        FootagePlanner planner = new FootagePlanner(library(), 2);
        assertThat(planner.choose(shot("s1", 0), List.of(new Pick(null, null), new Pick(999, List.of(998))))).isEmpty();
        assertThat(planner.choose(shot("s1", 0), picks())).isEmpty();
    }

    @Test
    void anyCopyOfAMomentGivesItsBestCopy() {
        List<Segment> copies = List.of(
                new Segment(1, A, 10, 13, 1, 1, 40, true, false),
                new Segment(2, B, 50, 56, 1, 1, 40, false, true));
        Choice choice = new FootagePlanner(copies, 2).choose(shot("s1", 0), picks(1)).orElseThrow();
        assertThat(choice.parts().get(0).segment().tile()).isEqualTo(2);
    }

    @Test
    void aCopyInAnotherSourceIsStillARepeat() {
        List<Segment> copies = List.of(
                new Segment(1, A, 10, 16, 1, 1, 40, false, false),
                new Segment(2, B, 50, 56, 1, 1, 40, false, true));
        FootagePlanner planner = new FootagePlanner(copies, 1);
        place(planner, shot("s1", 0), picks(2));
        assertThat(planner.choose(shot("s2", 400), picks(1))).isEmpty();
    }

    @Test
    void aShotWithoutAPickContinuesThePreviousScene() {
        List<Segment> scene = List.of(
                segment(1, A, 10, 15, 1, 1, 40, false),
                segment(2, A, 15, 21, 2, 2, 12, false),
                segment(3, B, 50, 56, 3, 3, 40, false));
        FootagePlanner planner = new FootagePlanner(scene, 2);
        place(planner, shot("s1", 0), List.of(new Pick(1, null)));
        Choice next = planner.continuation(shot("s2", 4)).orElseThrow();
        assertThat(next.moments()).containsExactly(2);
        assertThat(next.step()).isEqualTo(FootagePlanner.CONTINUATION);
    }

    @Test
    void continuationNeedsAShotRightBeforeAndAnUnusedMoment() {
        List<Segment> scene = List.of(
                segment(1, A, 10, 15, 1, 1, 40, false),
                segment(2, A, 15, 21, 2, 2, 40, false));
        FootagePlanner planner = new FootagePlanner(scene, 2);
        place(planner, shot("s1", 0), picks(1));
        assertThat(planner.continuation(shot("far", 100))).isEmpty();
        place(planner, shot("s2", 4), picks(2));
        assertThat(planner.continuation(shot("s3", 8))).isEmpty();
    }

    @Test
    void aWholeVideoNeverBreaksTheRules() {
        List<Segment> library = library();
        FootagePlanner planner = new FootagePlanner(library, 2);
        for (int i = 0; i < 60; i++) {
            Need need = shot("s" + i, i * 4.0);
            planner.choose(need, picks(1 + i % 10, 1 + (i + 1) % 10, 1 + (i + 2) % 10)).ifPresent(planner::commit);
        }
        assertThat(planner.uses().values()).allSatisfy(times -> {
            assertThat(times.size()).isLessThanOrEqualTo(2);
            if (times.size() == 2) {
                assertThat(Math.abs(times.get(0) - times.get(1))).isGreaterThanOrEqualTo(FootagePlanner.MIN_REUSE_GAP);
            }
        });
        assertThat(planner.placed().size()).isLessThanOrEqualTo(20);
    }
}
