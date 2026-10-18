package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.system.CheckResult;
import java.util.List;

public record SystemCheckDto(boolean ready, int failures, int warnings, List<CheckResult> checks) {

    public static SystemCheckDto from(List<CheckResult> checks) {
        int failures = (int) checks.stream().filter(c -> c.level() == CheckResult.Level.FAIL).count();
        int warnings = (int) checks.stream().filter(c -> c.level() == CheckResult.Level.WARN).count();
        return new SystemCheckDto(failures == 0, failures, warnings, checks);
    }
}
