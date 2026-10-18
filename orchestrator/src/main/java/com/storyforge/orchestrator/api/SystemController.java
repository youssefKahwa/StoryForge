package com.storyforge.orchestrator.api;

import com.storyforge.orchestrator.dto.CleanupDto;
import com.storyforge.orchestrator.dto.StorageDto;
import com.storyforge.orchestrator.dto.SystemCheckDto;
import com.storyforge.orchestrator.system.StorageService;
import com.storyforge.orchestrator.system.SystemCheckService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemCheckService checks;
    private final StorageService storage;

    public SystemController(SystemCheckService checks, StorageService storage) {
        this.checks = checks;
        this.storage = storage;
    }

    @GetMapping("/check")
    public SystemCheckDto check() {
        return SystemCheckDto.from(checks.run());
    }

    @GetMapping("/storage")
    public StorageDto storage(@RequestParam(defaultValue = "14") int olderThanDays) {
        return StorageDto.from(storage.usage(olderThanDays), olderThanDays);
    }

    @PostMapping("/cleanup")
    public CleanupDto cleanup(@RequestParam(defaultValue = "14") int olderThanDays) {
        return CleanupDto.from(storage.cleanup(olderThanDays));
    }
}
