package com.storyforge.orchestrator.sound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BeatGridTest {

    /** A drum-like hit (a short burst of decaying noise) on every beat, over a quiet hiss. */
    private static float[] clicks(double bpm, double phase, double seconds, long seed) {
        Random random = new Random(seed);
        float[] out = new float[(int) (seconds * BeatGrid.RATE)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (float) (random.nextGaussian() * 0.01);
        }
        double period = 60 / bpm;
        for (double t = phase; t < seconds; t += period) {
            int start = (int) Math.round(t * BeatGrid.RATE);
            for (int i = 0; i < BeatGrid.RATE * 0.03 && start + i < out.length; i++) {
                out[start + i] += (float) (random.nextGaussian() * 0.6 * Math.exp(-i / (BeatGrid.RATE * 0.008)));
            }
        }
        return out;
    }

    private static BeatGrid.Beats analyse(float[] samples) {
        return BeatGrid.analyse(BeatGrid.onsets(samples), samples.length / (double) BeatGrid.RATE);
    }

    @Test
    void findsTheTempoAndWhereTheBeatsAre() {
        for (double bpm : List.of(90.0, 120.0, 150.0)) {
            double phase = 0.31;
            BeatGrid.Beats beats = analyse(clicks(bpm, phase, 40, (long) bpm));
            assertThat(beats.bpm()).as("bpm " + bpm).isCloseTo(bpm, within(bpm * 0.01));
            assertThat(beats.confidence()).as("confidence at " + bpm).isGreaterThanOrEqualTo(BeatGrid.MIN_CONFIDENCE);
            double period = 60 / bpm;
            double offset = Math.abs(((beats.phase() - phase) % period + period) % period);
            assertThat(Math.min(offset, period - offset)).as("phase at " + bpm).isLessThan(0.02);
        }
    }

    @Test
    void hissWithoutAPulseHasNoGrid() {
        Random random = new Random(3);
        float[] hiss = new float[BeatGrid.RATE * 30];
        for (int i = 0; i < hiss.length; i++) {
            hiss[i] = (float) (random.nextGaussian() * 0.2);
        }
        assertThat(analyse(hiss).confidence()).isLessThan(BeatGrid.MIN_CONFIDENCE);
    }

    @Test
    void aSteadyPadHasNoGrid() {
        float[] pad = new float[BeatGrid.RATE * 30];
        for (int i = 0; i < pad.length; i++) {
            double t = i / (double) BeatGrid.RATE;
            pad[i] = (float) (0.2 * Math.sin(2 * Math.PI * 220 * t) + 0.1 * Math.sin(2 * Math.PI * 330 * t));
        }
        assertThat(analyse(pad).confidence()).isLessThan(BeatGrid.MIN_CONFIDENCE);
    }

    @Test
    void beatsAreLaidOutOverTheMusicSegmentAndLoopWithTheTrack() {
        BeatGrid.Beats beats = new BeatGrid.Beats(120, 0.5, 0.1, 3, 2.0, List.of(0.1, 0.6, 1.1, 1.6));
        List<Double> placed = BeatGrid.place(beats, 10, 15, 1.5);
        // 10.1-11.1 fall in the fade-in, 13.6 in the fade-out; the track loops at 12.
        assertThat(placed).containsExactly(11.6, 12.1, 12.6, 13.1);
    }

    @Test
    void theNearestBeatIsFoundOnlyWithinTheTolerance() {
        List<Double> beats = List.of(1.0, 1.5, 2.0);
        assertThat(BeatGrid.nearest(beats, 1.6, 0.2)).isEqualTo(1.5);
        assertThat(BeatGrid.nearest(beats, 1.85, 0.2)).isEqualTo(2.0);
        assertThat(BeatGrid.nearest(beats, 1.76, 0.2)).isNull();
        assertThat(BeatGrid.nearest(beats, 2.4, 0.2)).isNull();
        assertThat(BeatGrid.nearest(List.of(), 1.0, 0.2)).isNull();
    }

    @Test
    void theFftOfASineHasOnePeak() {
        double[] re = new double[64];
        double[] im = new double[64];
        for (int i = 0; i < 64; i++) {
            re[i] = Math.cos(2 * Math.PI * 5 * i / 64);
        }
        BeatGrid.fft(re, im);
        assertThat(Math.hypot(re[5], im[5])).isCloseTo(32, within(1e-6));
        assertThat(Math.hypot(re[7], im[7])).isLessThan(1e-6);
    }
}
