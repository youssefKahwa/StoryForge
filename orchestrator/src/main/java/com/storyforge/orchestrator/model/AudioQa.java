package com.storyforge.orchestrator.model;

import java.util.List;

public record AudioQa(double integratedLufs, double truePeakDb, double durationSec, boolean passed,
                      List<Track> tracks) {

    public record Track(String kind, String file, String title, LicenseRecord license) {
    }
}
