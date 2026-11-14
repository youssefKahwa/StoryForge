package com.storyforge.orchestrator.provider.clips;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.provider.clips.FootageIndex.Signature;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FootageIndexTest {

    private static final int SMALL = 144;

    /** A small grey frame of random blotches, the same for the same seed. */
    private static float[] frame(long seed, float brightness, float contrast) {
        Random random = new Random(seed);
        float[] out = new float[SMALL];
        for (int i = 0; i < SMALL; i++) {
            out[i] = brightness + (random.nextFloat() - 0.5f) * contrast;
        }
        return out;
    }

    private static float[][] shot(long seed, float brightness, float contrast, int frames) {
        float[][] out = new float[frames][];
        for (int i = 0; i < frames; i++) {
            out[i] = frame(seed, brightness, contrast);
        }
        return out;
    }

    @Test
    void aBrighterCopyOfTheSameFootageIsTheSameMoment() {
        Signature original = FootageIndex.signature(shot(1, 60, 80, 12));
        Signature brighter = FootageIndex.signature(shot(1, 110, 80, 12));
        Signature other = FootageIndex.signature(shot(2, 60, 80, 12));
        int[] groups = FootageIndex.group(List.of(original, brighter, other), new double[] {3, 3, 3},
                FootageIndex.MOMENT_CORRELATION);
        assertThat(groups[0]).isEqualTo(groups[1]);
        assertThat(groups[2]).isNotEqualTo(groups[0]);
    }

    @Test
    void aShortCopyInsideALongerOneMatchesInBothDirections() {
        float[][] whole = new float[20][];
        for (int i = 0; i < 20; i++) {
            whole[i] = frame(i < 10 ? 1 : 2, 60, 80);
        }
        Signature longer = FootageIndex.signature(whole);
        Signature partOfIt = FootageIndex.signature(shot(1, 70, 80, 6));
        assertThat(FootageIndex.correlation(partOfIt, longer)).isGreaterThan(0.99);
        // Only one of the longer shot's samples can be found in the short copy, so these are not the same moment.
        assertThat(FootageIndex.correlation(longer, partOfIt)).isLessThan(FootageIndex.MOMENT_CORRELATION);
    }

    @Test
    void flatFramesMatchNothingEvenEachOther() {
        Signature black = FootageIndex.signature(shot(1, 12, 1, 8));
        Signature alsoBlack = FootageIndex.signature(shot(2, 14, 1, 8));
        int[] groups = FootageIndex.group(List.of(black, alsoBlack), new double[] {2, 2},
                FootageIndex.MOMENT_CORRELATION);
        assertThat(groups[0]).isNotEqualTo(groups[1]);
    }

    @Test
    void theLongestCopyLeadsTheGroup() {
        Signature first = FootageIndex.signature(shot(1, 60, 80, 8));
        Signature second = FootageIndex.signature(shot(1, 60, 80, 8));
        int[] groups = FootageIndex.group(List.of(first, second), new double[] {2, 5},
                FootageIndex.MOMENT_CORRELATION);
        assertThat(groups).containsExactly(0, 0);
    }

    @Test
    void aSharpJumpIsAHiddenCutButAFadeIsNot() {
        float[][] frames = new float[12][];
        for (int i = 0; i < 6; i++) {
            frames[i] = frame(1, 70, 200);
        }
        for (int i = 6; i < 12; i++) {
            frames[i] = frame(2, 70, 200);
        }
        assertThat(FootageIndex.withHiddenCuts(List.of(0.0, 3.0), frames, 4)).containsExactly(0.0, 1.5, 3.0);

        float[][] fade = new float[12][];
        for (int i = 0; i < 12; i++) {
            fade[i] = frame(1, 200 - i * 4, 20);
        }
        fade[6] = frame(1, 100, 20);
        assertThat(FootageIndex.withHiddenCuts(List.of(0.0, 3.0), fade, 4)).containsExactly(0.0, 3.0);
    }

    @Test
    void cutsCloserThanAFewFramesCountOnce() {
        float[][] still = shot(1, 70, 50, 8);
        assertThat(FootageIndex.withHiddenCuts(List.of(0.0, 1.0, 1.2, 2.0), still, 4)).containsExactly(0.0, 1.0, 2.0);
    }

    @Test
    void framesAreAveragedDownToSixteenByNine() {
        byte[] raw = new byte[64 * 36 * 2];
        java.util.Arrays.fill(raw, (byte) 200);
        float[][] small = FootageIndex.pooled(raw);
        assertThat(small).hasNumberOfRows(2);
        assertThat(small[0]).hasSize(SMALL);
        assertThat(small[1][143]).isEqualTo(200f);
    }

    @Test
    void theBestCopyIsLongButNeverTheLowQualityOne() {
        assertThat(FootageIndex.copyScore(1920, 6, 10, false)).isGreaterThan(FootageIndex.copyScore(1920, 3, 10, false));
        assertThat(FootageIndex.copyScore(1920, 5, 10, false)).isGreaterThan(FootageIndex.copyScore(854, 6, 10, false));
        assertThat(FootageIndex.copyScore(854, 8, 10, false)).isGreaterThan(FootageIndex.copyScore(1920, 1.5, 10, false));
        assertThat(FootageIndex.copyScore(1920, 1.5, 10, false)).isGreaterThan(FootageIndex.copyScore(640, 9, 40, false));
        assertThat(FootageIndex.copyScore(1920, 6, 10, false)).isGreaterThan(FootageIndex.copyScore(1920, 6, 10, true));
    }

    @Test
    void bothAnswerFormatsOfTheEditorStillRead() {
        JsonMapper json = JsonMapper.builder().build();
        FilmClipFinder.Matches ranked = json.readValue("""
                {"matches": [{"shot": "s1", "picks": [{"tile": 7, "more": [8]}, {"tile": 15}]}]}""",
                FilmClipFinder.Matches.class);
        assertThat(ranked.matches().get(0).ranked()).extracting(FootagePlanner.Pick::tile).containsExactly(7, 15);
        assertThat(ranked.matches().get(0).ranked().get(0).more()).containsExactly(8);

        FilmClipFinder.Matches older = json.readValue("""
                {"matches": [{"shot": "s1", "tile": 7, "more": [8, 9]}, {"shot": "s2"}, {"shot": "s3", "picks": []}]}""",
                FilmClipFinder.Matches.class);
        assertThat(older.matches().get(0).ranked()).extracting(FootagePlanner.Pick::tile).containsExactly(7);
        assertThat(older.matches().get(0).ranked().get(0).more()).containsExactly(8, 9);
        assertThat(older.matches().get(1).ranked()).isEmpty();
        assertThat(older.matches().get(2).ranked()).isEmpty();
    }

    @Test
    void darkMomentsAreLiftedWithinLimitsAndBrightOnesAreNot() {
        assertThat(FilmClipFinder.look(80)).contains("gamma=1.00");
        assertThat(FilmClipFinder.look(46)).contains("gamma=1.00");
        assertThat(FilmClipFinder.look(22)).contains("gamma=1.28");
        assertThat(FilmClipFinder.look(2)).contains("gamma=1.32");
    }
}
