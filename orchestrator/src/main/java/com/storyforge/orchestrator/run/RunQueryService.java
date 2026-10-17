package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import com.storyforge.orchestrator.cost.CostGuard;
import com.storyforge.orchestrator.dto.RunDetailDto;
import com.storyforge.orchestrator.dto.RunDto;
import com.storyforge.orchestrator.stage.StageExecution;
import com.storyforge.orchestrator.stage.StageExecutionService;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RunQueryService {

    private final RunService runs;
    private final StageExecutionService executions;
    private final ArtifactStore artifacts;
    private final CostGuard costs;

    public RunQueryService(RunService runs, StageExecutionService executions, ArtifactStore artifacts,
                           CostGuard costs) {
        this.runs = runs;
        this.executions = executions;
        this.artifacts = artifacts;
        this.costs = costs;
    }

    public List<RunDto> list() {
        List<Run> all = runs.list();
        Map<Long, List<StageExecution>> byRun = executions.forRuns(all.stream().map(Run::getId).toList()).stream()
                .collect(Collectors.groupingBy(StageExecution::getRunId));
        return all.stream().map(run -> RunDto.from(run, byRun.getOrDefault(run.getId(), List.of()))).toList();
    }

    public RunDto summary(long id) {
        return RunDto.from(runs.get(id), executions.forRun(id));
    }

    public RunDetailDto detail(long id) {
        Run run = runs.get(id);
        return RunDetailDto.from(run, executions.forRun(id), artifacts.list(id), costs.spent(id));
    }
}
