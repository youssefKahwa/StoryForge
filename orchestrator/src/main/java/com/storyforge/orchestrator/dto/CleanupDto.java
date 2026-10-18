package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.system.StorageService;

public record CleanupDto(int runsCleaned, long bytesFreed, String freed) {

    public static CleanupDto from(StorageService.Cleanup cleanup) {
        return new CleanupDto(cleanup.runsCleaned(), cleanup.bytesFreed(), StorageService.human(cleanup.bytesFreed()));
    }
}
