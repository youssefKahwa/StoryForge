package com.storyforge.orchestrator.topic;

import com.storyforge.orchestrator.api.NotFoundException;
import com.storyforge.orchestrator.config.StoryForgeProperties;
import com.storyforge.orchestrator.dto.CreateSeedTopicRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeedTopicService {

    private final SeedTopicRepository repository;
    private final StoryForgeProperties properties;

    public SeedTopicService(SeedTopicRepository repository, StoryForgeProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<SeedTopic> listActive() {
        return repository.findByStatusInOrderByCreatedAtDesc(List.of(SeedTopicStatus.NEW, SeedTopicStatus.USED));
    }

    @Transactional(readOnly = true)
    public List<SeedTopic> backlog() {
        return repository.findTop20ByStatusOrderByCreatedAtAsc(SeedTopicStatus.NEW);
    }

    @Transactional(readOnly = true)
    public SeedTopic get(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Seed topic " + id + " not found"));
    }

    @Transactional
    public SeedTopic create(CreateSeedTopicRequest request) {
        SeedTopic topic = new SeedTopic();
        topic.setTitle(request.title().trim());
        topic.setNotes(request.notes());
        topic.setFormat(request.format() != null ? request.format() : properties.defaults().format());
        topic.setLanguage(request.language() != null ? request.language() : properties.defaults().language());
        topic.setStatus(SeedTopicStatus.NEW);
        topic.setCreatedAt(Instant.now());
        return repository.save(topic);
    }

    @Transactional
    public void markUsed(long id) {
        get(id).setStatus(SeedTopicStatus.USED);
    }

    @Transactional
    public void archive(long id) {
        get(id).setStatus(SeedTopicStatus.ARCHIVED);
    }
}
