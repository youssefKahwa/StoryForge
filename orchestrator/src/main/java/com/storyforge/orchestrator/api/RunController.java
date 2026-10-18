package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.CreateRunRequest;
import com.storyforge.orchestrator.dto.RunDetailDto;
import com.storyforge.orchestrator.dto.RunDto;
import com.storyforge.orchestrator.run.PublicationService;
import com.storyforge.orchestrator.run.RunQueryService;
import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.stage.Stage;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final RunService runs;
    private final RunQueryService queries;
    private final PublicationService publications;

    public RunController(RunService runs, RunQueryService queries, PublicationService publications) {
        this.runs = runs;
        this.queries = queries;
        this.publications = publications;
    }

    @GetMapping
    public List<RunDto> list() {
        return queries.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RunDto create(@Valid @RequestBody CreateRunRequest request) {
        return queries.summary(runs.create(request).getId());
    }

    @GetMapping("/{id}")
    public RunDetailDto get(@PathVariable long id) {
        return queries.detail(id);
    }

    @PostMapping("/{id}/cancel")
    public RunDto cancel(@PathVariable long id) {
        runs.cancel(id);
        return queries.summary(id);
    }

    @PostMapping("/{id}/resume")
    public RunDto resume(@PathVariable long id) {
        runs.resume(id);
        return queries.summary(id);
    }

    @PostMapping("/{id}/veto")
    public RunDto veto(@PathVariable long id) {
        publications.veto(id);
        return queries.summary(id);
    }

    @PostMapping("/{id}/stages/{stage}/rerun")
    public RunDto rerun(@PathVariable long id, @PathVariable String stage,
                        @RequestParam(defaultValue = "false") boolean fresh) {
        Stage target = Stage.fromFolder(stage)
                .orElseThrow(() -> new IllegalArgumentException("Unknown stage " + stage));
        runs.rerunFrom(id, target, fresh);
        return queries.summary(id);
    }
}
