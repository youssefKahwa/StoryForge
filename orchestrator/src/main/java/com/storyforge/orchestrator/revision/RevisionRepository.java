package com.storyforge.orchestrator.revision;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionRepository extends JpaRepository<Revision, Long> {

    List<Revision> findByRunIdOrderByCreatedAtAsc(Long runId);

    List<Revision> findByRunIdAndStatus(Long runId, RevisionStatus status);
}
