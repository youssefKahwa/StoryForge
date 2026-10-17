package com.storyforge.orchestrator.stage;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StageExecutionRepository extends JpaRepository<StageExecution, Long> {

    List<StageExecution> findByRunIdOrderByIdAsc(Long runId);

    List<StageExecution> findByRunIdInOrderByIdAsc(Collection<Long> runIds);

    boolean existsByRunIdAndStageAndStatus(Long runId, Stage stage, StageStatus status);
}
