package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.system.StorageService;

public record StorageDto(long freeBytes, String free, long runsBytes, String runs, long exportsBytes, String exports,
                         long cleanableBytes, String cleanable, int cleanableRuns, int olderThanDays) {

    public static StorageDto from(StorageService.Usage usage, int olderThanDays) {
        return new StorageDto(usage.freeBytes(), StorageService.human(usage.freeBytes()), usage.runsBytes(),
                StorageService.human(usage.runsBytes()), usage.exportsBytes(),
                StorageService.human(usage.exportsBytes()), usage.cleanableBytes(),
                StorageService.human(usage.cleanableBytes()), usage.cleanableRuns(), olderThanDays);
    }
}
