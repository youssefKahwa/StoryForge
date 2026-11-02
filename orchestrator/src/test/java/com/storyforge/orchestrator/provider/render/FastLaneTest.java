package com.storyforge.orchestrator.provider.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Edl;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import com.storyforge.orchestrator.model.Edl.EdlPunch;
import com.storyforge.orchestrator.model.Edl.EdlShot;
import com.storyforge.orchestrator.model.Edl.EdlStyle;
import java.util.List;
import org.junit.jupiter.api.Test;

class FastLaneTest {

    private static EdlShot shot(String id, String type, double start, double end, String transition, String media,
                                EdlPunch punch) {
        return new EdlShot(id, "01", type, start, end, "assets/" + id + ".mp4", "push_in", null, transition, media,
                null, null, punch);
    }

    private static Edl edl(List<EdlShot> shots, List<EdlChapter> chapters, boolean burn) {
        double end = shots.get(shots.size() - 1).end();
        return new Edl(2, 1920, 1080, 30, end, null, shots, List.of(), List.of(), chapters,
                new EdlStyle(List.of(), List.of(), burn), List.of(), null);
    }

    @Test
    void plainShotsGoToFfmpegAndDesignedOnesToRemotion() {
        Edl edl = edl(List.of(
                shot("a", "establishing", 0, 10, "cut", "clip", null),
                shot("b", "kinetic_text", 10, 14, "cut", "ai", null),
                shot("c", "detail", 14, 30, "zoom_in", "footage", null),
                shot("d", "detail", 30, 40, "cut", "photo", null)), List.of(), false);
        List<FastLane.Range> ranges = FastLane.plan(edl);

        assertThat(ranges.get(0)).isEqualTo(new FastLane.Range(0, 300, false));
        // The text card, then the first 20 frames after the zoom transition (merged: gap < 24 frames).
        assertThat(ranges.get(1)).isEqualTo(new FastLane.Range(300, 440, true));
        assertThat(ranges.get(2)).isEqualTo(new FastLane.Range(440, 900, false));
        // Photos and the outro are drawn by Remotion.
        assertThat(ranges.get(3)).isEqualTo(new FastLane.Range(900, 1200, true));
        assertThat(ranges).hasSize(4);
        assertThat(ranges.stream().mapToInt(FastLane.Range::frames).sum()).isEqualTo(1200);
    }

    @Test
    void chaptersAfterTheFirstAreDesigned() {
        Edl edl = edl(List.of(shot("a", "detail", 0, 60, "cut", "clip", null)),
                List.of(new EdlChapter(0, "One"), new EdlChapter(20, "Two")), false);
        assertThat(FastLane.plan(edl)).contains(new FastLane.Range(600, 705, true));
    }

    @Test
    void burnedCaptionsRenderEverythingWithRemotion() {
        Edl edl = edl(List.of(shot("a", "detail", 0, 60, "cut", "clip", null)), List.of(), true);
        assertThat(FastLane.plan(edl)).containsExactly(new FastLane.Range(0, 1800, true));
    }

    @Test
    void piecesSplitAtThePunchIn() {
        EdlShot a = shot("a", "detail", 0, 10, "cut", "clip", new EdlPunch(4, 0.3, 0.4, 1.2));
        Edl edl = edl(List.of(a, shot("b", "detail", 10, 20, "cut", "host", null)), List.of(), false);
        List<FastLane.Piece> pieces = FastLane.pieces(edl, new FastLane.Range(0, 400, false));

        assertThat(pieces).hasSize(3);
        assertThat(pieces.get(0).from()).isZero();
        assertThat(pieces.get(0).to()).isEqualTo(120);
        assertThat(pieces.get(0).punched()).isFalse();
        assertThat(pieces.get(1).from()).isEqualTo(120);
        assertThat(pieces.get(1).punched()).isTrue();
        assertThat(pieces.get(2).shot().id()).isEqualTo("b");
        assertThat(pieces.get(2).to()).isEqualTo(400);
    }

    @Test
    void filterFitsTheFrameMovesAndFlashesOnThePunch() {
        EdlShot a = shot("a", "detail", 0, 10, "cut", "clip", new EdlPunch(4, 0.3, 0.4, 1.2));
        FastLane.Piece punched = new FastLane.Piece(a, 120, 300, true, 0, 300);
        String filter = FastLane.filter(punched, 1920, 1080, 30);

        assertThat(filter).startsWith("fps=30,scale=1920:1080:force_original_aspect_ratio=increase,crop=1920:1080");
        assertThat(filter).contains("eval=frame").contains("1.2000").contains("576.0").contains("432.0");
        assertThat(filter).contains("if(eq(n,0),0.22");
        assertThat(filter).doesNotContain(",,");

        FastLane.Piece still = new FastLane.Piece(shot("s", "detail", 0, 5, "cut", "ai", null), 0, 150, false, 0, 150);
        assertThat(FastLane.filter(still, 1920, 1080, 30)).contains("pow(").contains(":eval=init");
    }
}
