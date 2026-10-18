package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);
    private static final String SCRIPT = "Add-Type -Namespace StoryForge -Name Power -MemberDefinition "
            + "'[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'; "
            + "while (Get-Process -Id %d -ErrorAction SilentlyContinue) { "
            + "[StoryForge.Power]::SetThreadExecutionState(0x80000001) | Out-Null; Start-Sleep -Seconds 30 }";

    private final boolean enabled;
    private Process process;

    public KeepAwake(StoryForgeProperties properties) {
        this.enabled = properties.keepAwake()
                && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    public synchronized void acquire() {
        if (!enabled || (process != null && process.isAlive())) {
            return;
        }
        try {
            String script = SCRIPT.formatted(ProcessHandle.current().pid());
            process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            log.info("Keeping Windows awake while the run is active");
        } catch (IOException e) {
            log.warn("Cannot prevent sleep: {}", e.getMessage());
        }
    }

    public synchronized void release() {
        if (process != null) {
            process.destroy();
            process = null;
        }
    }
}
