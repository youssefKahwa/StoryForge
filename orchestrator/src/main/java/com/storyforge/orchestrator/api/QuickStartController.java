package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.QuickStartDto;
import com.storyforge.orchestrator.dto.QuickStartRequest;
import com.storyforge.orchestrator.dto.SeedTopicDto;
import com.storyforge.orchestrator.run.QuickStartService;
import com.storyforge.orchestrator.topic.TopicIdeaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuickStartController {

    private final QuickStartService quickStart;
    private final TopicIdeaService ideas;

    public QuickStartController(QuickStartService quickStart, TopicIdeaService ideas) {
        this.quickStart = quickStart;
        this.ideas = ideas;
    }

    @PostMapping("/api/quick-start")
    public QuickStartDto start(@Valid @RequestBody QuickStartRequest request) {
        return QuickStartDto.from(quickStart.start(request.urls(), request.pastedText(), request.topic(),
                request.notes()));
    }

    @PostMapping("/api/seed-topics/suggest")
    public List<SeedTopicDto> suggest(@RequestParam(defaultValue = "5") int count) {
        return ideas.suggest(count).stream().map(SeedTopicDto::from).toList();
    }
}
