package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.ProgressDto;
import com.storyforge.orchestrator.run.ProgressService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgressController {

    private final ProgressService progress;

    public ProgressController(ProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/api/runs/{runId}/progress")
    public ProgressDto progress(@PathVariable long runId) {
        return ProgressDto.from(progress.progress(runId));
    }
}
