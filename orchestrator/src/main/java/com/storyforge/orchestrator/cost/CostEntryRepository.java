package com.storyforge.orchestrator.cost;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostEntryRepository extends JpaRepository<CostEntry, Long> {

    @Query("select coalesce(sum(c.costUsd), 0) from CostEntry c where c.runId = :runId")
    BigDecimal totalForRun(@Param("runId") Long runId);
}
