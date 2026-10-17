package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.CreateSeedTopicRequest;
import com.storyforge.orchestrator.dto.SeedTopicDto;
import com.storyforge.orchestrator.topic.SeedTopicService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seed-topics")
public class SeedTopicController {

    private final SeedTopicService seedTopics;

    public SeedTopicController(SeedTopicService seedTopics) {
        this.seedTopics = seedTopics;
    }

    @GetMapping
    public List<SeedTopicDto> list() {
        return seedTopics.listActive().stream().map(SeedTopicDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeedTopicDto create(@Valid @RequestBody CreateSeedTopicRequest request) {
        return SeedTopicDto.from(seedTopics.create(request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable long id) {
        seedTopics.archive(id);
    }
}
