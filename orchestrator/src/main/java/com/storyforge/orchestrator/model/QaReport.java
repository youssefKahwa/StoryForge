package com.storyforge.orchestrator.model;

import java.time.Instant;
import java.util.List;

public record QaReport(boolean passed, long criticalFailures, long warnings, boolean disclosureRequired,
                       Instant generatedAt, List<Check> checks) {

    public enum Severity {
        CRITICAL,
        WARNING,
        INFO
    }

    public record Check(String id, String category, Severity severity, boolean passed, String detail) {
    }

    public static QaReport of(List<Check> checks, boolean disclosureRequired) {
        long critical = checks.stream().filter(c -> !c.passed() && c.severity() == Severity.CRITICAL).count();
        long warnings = checks.stream().filter(c -> !c.passed() && c.severity() == Severity.WARNING).count();
        return new QaReport(critical == 0, critical, warnings, disclosureRequired, Instant.now(), checks);
    }
}
