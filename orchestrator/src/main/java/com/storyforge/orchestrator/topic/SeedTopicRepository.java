package com.storyforge.orchestrator.topic;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedTopicRepository extends JpaRepository<SeedTopic, Long> {

    List<SeedTopic> findByStatusInOrderByCreatedAtDesc(Collection<SeedTopicStatus> statuses);

    List<SeedTopic> findTop20ByStatusOrderByCreatedAtAsc(SeedTopicStatus status);
}
