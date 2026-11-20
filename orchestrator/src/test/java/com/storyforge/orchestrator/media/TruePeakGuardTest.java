package com.storyforge.orchestrator.media;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TruePeakGuardTest {

    @Test
    void onlyAFileCloseToTheLimitIsEncodedAgain() {
        // Run 166: +2.24 dBTP after two AAC encodes of a mix limited at -2.7 dBFS.
        assertThat(FfmpegService.needsPeakGuard(2.24, -1)).isTrue();
        assertThat(FfmpegService.needsPeakGuard(-1.1, -1)).isTrue();
        assertThat(FfmpegService.needsPeakGuard(-1.36, -1)).isFalse();
    }

    @Test
    void theLimiterRunsAtFourTimesTheRateToCatchPeaksBetweenSamples() {
        String filter = FfmpegService.truePeakLimiter(-2.5);
        assertThat(filter).startsWith("aresample=192000,alimiter=limit=0.7499").endsWith("aresample=48000");
    }
}
