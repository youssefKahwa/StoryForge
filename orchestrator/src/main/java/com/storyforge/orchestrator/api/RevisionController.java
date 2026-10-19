package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.artifact.RunFiles;
import com.storyforge.orchestrator.dto.CreateRevisionRequest;
import com.storyforge.orchestrator.dto.RevisionDto;
import com.storyforge.orchestrator.dto.RunDto;
import com.storyforge.orchestrator.dto.TimelineDto;
import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.AssetManifest;
import com.storyforge.orchestrator.model.Scene;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.VideoPackage;
import com.storyforge.orchestrator.revision.RevisionService;
import com.storyforge.orchestrator.run.RunQueryService;
import com.storyforge.orchestrator.run.RunService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.worker.VisualPlanStage;
import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api/runs/{runId}")
public class RevisionController {

    private final RevisionService revisions;
    private final RunService runs;
    private final ArtifactStore artifacts;
    private final RunQueryService queries;

    public RevisionController(RevisionService revisions, RunService runs, ArtifactStore artifacts,
                              RunQueryService queries) {
        this.revisions = revisions;
        this.queries = queries;
        this.runs = runs;
        this.artifacts = artifacts;
    }

    @GetMapping("/revisions")
    public List<RevisionDto> list(@PathVariable long runId) {
        return revisions.list(runId).stream().map(RevisionDto::from).toList();
    }

    @PostMapping("/revisions")
    public RevisionDto add(@PathVariable long runId, @Valid @RequestBody CreateRevisionRequest request) {
        return RevisionDto.from(revisions.add(runId, request.kind(), request.target(), request.atSec(),
                request.note()));
    }

    @DeleteMapping("/revisions/{revisionId}")
    public void delete(@PathVariable long runId, @PathVariable long revisionId) {
        revisions.delete(runId, revisionId);
    }

    @PostMapping("/revisions/apply")
    public RunDto apply(@PathVariable long runId) {
        revisions.apply(runId);
        return queries.summary(runId);
    }

    @GetMapping("/timeline")
    public TimelineDto timeline(@PathVariable long runId) {
        runs.get(runId);
        RunFiles files = artifacts.forRun(runId);
        if (!files.exists(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST) || !files.exists(Stage.ALIGN, "alignment.json")) {
            throw new NotFoundException("This run has no timeline yet");
        }
        ShotList shots = files.read(Stage.VISUAL_PLAN, VisualPlanStage.SHOTLIST, ShotList.class);
        Map<String, String> assets = new HashMap<>();
        if (files.exists(Stage.ASSETS, "assets.json")) {
            files.read(Stage.ASSETS, "assets.json", AssetManifest.class).assets()
                    .forEach(asset -> assets.put(asset.shotId(), asset.file()));
        }
        List<Scene> scenes = files.read(Stage.CRITIQUE, "scenes.json", new TypeReference<List<Scene>>() { });
        Alignment alignment = files.read(Stage.ALIGN, "alignment.json", Alignment.class);
        VideoPackage pkg = files.exists(Stage.PACKAGE, "package.json")
                ? files.read(Stage.PACKAGE, "package.json", VideoPackage.class) : null;
        return TimelineDto.from(shots, assets, scenes, alignment, pkg);
    }
}
