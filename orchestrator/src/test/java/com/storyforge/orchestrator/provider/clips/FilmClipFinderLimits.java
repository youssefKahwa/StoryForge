package com.storyforge.orchestrator.provider.clips;

/** Test access to the package-private fair-use limits. */
public final class FilmClipFinderLimits {

    private FilmClipFinderLimits() {
    }

    public static int maxClips(double durationSec) {
        return FilmClipFinder.maxClips(durationSec);
    }
}
