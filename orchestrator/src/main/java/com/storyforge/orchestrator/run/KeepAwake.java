package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.config.StoryForgeProperties;
import java.io.IOException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Keeps Windows awake while a run is active, through a small PowerShell loop calling SetThreadExecutionState.
 * <ul>
 *   <li>The display is kept on too (ES_DISPLAY_REQUIRED): on a Modern Standby PC, "system required" alone does not
 *   stop the screen-off timer from putting the PC into standby (run 198's render stalled three times overnight,
 *   Kernel-Power events 506/507). Closing the lid still sleeps.</li>
 *   <li>The script goes in as -EncodedCommand: passed as a plain argument, the quotes around "kernel32.dll" were lost
 *   on the command line, the type never compiled, and the loop silently did nothing.</li>
 *   <li>The flags are written in decimal: Windows PowerShell reads 0x80000003 as a negative Int32, which does not
 *   convert to the uint the function takes.</li>
 * </ul>
 */
@Component
public class KeepAwake {

    private static final Logger log = LoggerFactory.getLogger(KeepAwake.class);
    /** ES_CONTINUOUS | ES_SYSTEM_REQUIRED | ES_DISPLAY_REQUIRED (0x80000003). */
    static final long FLAGS = 0x80000003L;
    private static final String SCRIPT = "Add-Type -Namespace StoryForge -Name Power -MemberDefinition "
            + "'[DllImport(\"kernel32.dll\")] public static extern uint SetThreadExecutionState(uint f);'; "
            + "while (Get-Process -Id %d -ErrorAction SilentlyContinue) { "
            + "[StoryForge.Power]::SetThreadExecutionState([uint32]" + FLAGS + ") | Out-Null; Start-Sleep -Seconds 30 }";

    /** The script as PowerShell's -EncodedCommand wants it: base64 of its UTF-16LE bytes. */
    static String encoded(long pid) {
        return java.util.Base64.getEncoder().encodeToString(SCRIPT.formatted(pid)
                .getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
    }

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
            process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand",
                    encoded(ProcessHandle.current().pid()))
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
