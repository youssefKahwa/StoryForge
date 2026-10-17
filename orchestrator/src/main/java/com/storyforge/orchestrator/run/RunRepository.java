package com.storyforge.orchestrator.run;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RunRepository extends JpaRepository<Run, Long> {

    Optional<Run> findFirstByStatusOrderByCreatedAtAsc(RunStatus status);

    List<Run> findByStatus(RunStatus status);

    List<Run> findAllByOrderByCreatedAtDesc();
}
