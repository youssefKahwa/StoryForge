package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.artifact.StoredFile;

public record RunFileDto(String stage, String name, long sizeBytes, String url) {

    public static RunFileDto from(long runId, StoredFile file) {
        return new RunFileDto(file.stage(), file.name(), file.sizeBytes(),
                "/api/runs/" + runId + "/files/" + file.stage() + "/" + file.name());
    }
}
