package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.artifact.ArtifactStore;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RunFileController {

    private final ArtifactStore artifacts;

    public RunFileController(ArtifactStore artifacts) {
        this.artifacts = artifacts;
    }

    @GetMapping("/api/runs/{id}/files/{stage}/{name:.+}")
    public ResponseEntity<Resource> file(@PathVariable long id, @PathVariable String stage,
                                         @PathVariable String name) {
        Path file = artifacts.resolve(id, stage, name)
                .orElseThrow(() -> new NotFoundException("File " + stage + "/" + name + " not found"));
        Resource resource = new FileSystemResource(file);
        MediaType type = name.endsWith(".md") || name.endsWith(".srt")
                ? MediaType.parseMediaType("text/plain;charset=UTF-8")
                : MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type).body(resource);
    }
}
