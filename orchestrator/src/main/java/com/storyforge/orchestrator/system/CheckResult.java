package com.storyforge.orchestrator.system;

public record CheckResult(String key, String label, Level level, String detail, String fix) {

    public enum Level { OK, WARN, FAIL, OFF }

    static CheckResult ok(String key, String label, String detail) {
        return new CheckResult(key, label, Level.OK, detail, null);
    }

    static CheckResult warn(String key, String label, String detail, String fix) {
        return new CheckResult(key, label, Level.WARN, detail, fix);
    }

    static CheckResult fail(String key, String label, String detail, String fix) {
        return new CheckResult(key, label, Level.FAIL, detail, fix);
    }

    static CheckResult off(String key, String label, String detail) {
        return new CheckResult(key, label, Level.OFF, detail, null);
    }
}
