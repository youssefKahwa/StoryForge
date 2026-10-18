package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.VoiceDto;
import com.storyforge.orchestrator.voice.VoiceCatalog;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/voices")
public class VoiceController {

    private final VoiceCatalog voices;

    public VoiceController(VoiceCatalog voices) {
        this.voices = voices;
    }

    @GetMapping
    public List<VoiceDto> list() {
        return voices.voices().stream().map(voice -> VoiceDto.from(voice, voices.defaultVoice())).toList();
    }

    @GetMapping("/{id}/sample")
    public ResponseEntity<Resource> sample(@PathVariable String id) {
        Resource file = new FileSystemResource(voices.sample(id)
                .orElseThrow(() -> new NotFoundException("Unknown voice " + id)));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/wav")).body(file);
    }
}
