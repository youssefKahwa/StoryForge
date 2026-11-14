package com.storyforge.orchestrator.provider.clips;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.provider.clips.CreatorShots.Part;
import com.storyforge.orchestrator.provider.clips.CreatorShots.Window;
import com.storyforge.orchestrator.provider.clips.FootageIndex.Look;
import com.storyforge.orchestrator.provider.clips.FootageIndex.Segment;
import com.storyforge.orchestrator.provider.media.MediaSvcClient;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class CreatorShotsTest {

    private static final int W = 64;
    private static final int H = 36;
    private static final MediaSvcClient.Trailer SOURCE = new MediaSvcClient.Trailer(Path.of("a.mp4"), "a", "A", "c",
            "u", 100);

    /** A blocky 64x36 grey picture, the same for the same seed. */
    private static byte[] picture(long seed) {
        Random random = new Random(seed);
        int[] blocks = new int[16 * 9];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = 20 + random.nextInt(200);
        }
        byte[] out = new byte[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                out[y * W + x] = (byte) Math.min(255, blocks[(y / 4) * 16 + x / 4] + random.nextInt(6));
            }
        }
        return out;
    }

    private static byte[] brighter(byte[] frame) {
        byte[] out = new byte[frame.length];
        for (int i = 0; i < frame.length; i++) {
            out[i] = (byte) Math.min(255, (int) ((frame[i] & 0xFF) * 0.8 + 30));
        }
        return out;
    }

    private static byte[] mirror(byte[] frame) {
        return FootageIndex.mirrored(frame, 0);
    }

    private static Look look(int tile, int moment, byte[] frame) {
        Segment segment = new Segment(tile, SOURCE, tile * 10.0, tile * 10.0 + 4, moment, moment, 40, false, true);
        return new Look(segment, new float[][] {FootageIndex.unit(FootageIndex.pooled(frame)[0])},
                new float[][] {FootageIndex.fine(frame, 0)});
    }

    @Test
    void theMainVideoIsCutIntoPiecesOfHalfASecondOrMore() {
        List<double[]> pieces = CreatorShots.pieces(List.of(0.0, 1.0, 1.3, 4.0), 5);
        assertThat(pieces).hasSize(3);
        assertThat(pieces.get(1)).containsExactly(1.3, 4.0);
        assertThat(pieces.get(2)).containsExactly(4.0, 5.0);
    }

    @Test
    void piecesAreGroupedIntoWindowsOfAboutSixSeconds() {
        List<Window> windows = CreatorShots.windows(List.of(new double[] {0, 2}, new double[] {2, 4},
                new double[] {4, 7}, new double[] {7, 20}));
        assertThat(windows).containsExactly(new Window(0, 7), new Window(7, 13), new Window(13, 20));
    }

    @Test
    void theSameFootageIsFoundBrighterOrMirroredAndOtherFootageIsNot() {
        byte[] a = picture(1);
        byte[] b = picture(2);
        byte[] c = picture(3);
        List<Look> library = List.of(look(1, 11, a), look(2, 12, b));
        byte[] main = new byte[W * H * 12];
        System.arraycopy(brighter(a), 0, main, 2 * W * H, W * H);
        System.arraycopy(mirror(b), 0, main, 6 * W * H, W * H);
        System.arraycopy(c, 0, main, 10 * W * H, W * H);
        List<Part> parts = CreatorShots.exact(List.of(new double[] {0, 1}, new double[] {1, 2}, new double[] {2, 3}),
                main, library);
        assertThat(parts).extracting(Part::moment).containsExactly(11, 12);
        assertThat(parts).extracting(Part::start).containsExactly(0.0, 1.0);
    }

    @Test
    void theEditorSeesWhatWasSaidAndShownAboutEveryTenSeconds() {
        CreatorShots.Timeline timeline = new CreatorShots.Timeline(1, "k", List.of(
                new Part(2, 5, 3, null, true),
                new Part(12, 18, null, "narrator in the office rubbing his temples", true),
                new Part(25, 30, null, "stock photo of a crying child", false)));
        List<VideoTranscript.Line> said = List.of(new VideoTranscript.Line(1, 3, "hello there"),
                new VideoTranscript.Line(13, 15, "the \"office\" part"), new VideoTranscript.Line(26, 28, "third"));
        String lines = CreatorShots.lines(timeline, said, Map.of(3, 7)::get);
        assertThat(lines.split("\n")).containsExactly(
                "[0:00] said: \"hello there\" | showed: 7",
                "[0:10] said: \"the 'office' part\" | looks like: narrator in the office rubbing his temples");
    }

    @Test
    void coverageIsTheShareOfTheCreatorsFootageTheLibraryHolds() {
        CreatorShots.Timeline timeline = new CreatorShots.Timeline(1, "k", List.of(
                new Part(0, 5, 3, null, true),
                new Part(0, 20, null, "film", true),
                new Part(20, 30, null, "meme", false)));
        assertThat(timeline.coverage()).isEqualTo(0.25);
    }
}
