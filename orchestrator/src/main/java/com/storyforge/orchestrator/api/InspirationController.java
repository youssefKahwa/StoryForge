package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.CreateInspirationRequest;
import com.storyforge.orchestrator.dto.InspirationDto;
import com.storyforge.orchestrator.inspiration.Inspiration;
import com.storyforge.orchestrator.inspiration.InspirationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inspirations")
public class InspirationController {

    private final InspirationService inspirations;

    public InspirationController(InspirationService inspirations) {
        this.inspirations = inspirations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public InspirationDto create(@Valid @RequestBody CreateInspirationRequest request) {
        return view(inspirations.create(request));
    }

    @GetMapping("/{id}")
    public InspirationDto get(@PathVariable long id) {
        return view(inspirations.get(id));
    }

    private InspirationDto view(Inspiration inspiration) {
        long id = inspiration.getId();
        return InspirationDto.from(inspiration, inspirations.transcripts(id), inspirations.ideas(id).orElse(null));
    }
}
