package com.storyforge.orchestrator.run;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Whether this Windows laptop runs on battery. On battery the CPU is throttled (renders ~10x slower), so the renderer
 * uses fewer parallel tabs and System check asks to plug in. Any error means "not on battery".
 */
@Component
public class PowerStatus {

    private static final long CACHE_MILLIS = 60_000;
    private static final String QUERY =
            "(Get-CimInstance Win32_Battery | Select-Object -First 1 -ExpandProperty BatteryStatus)";

    private volatile long checkedAt;
    private volatile boolean onBattery;

    public boolean onBattery() {
        long now = System.currentTimeMillis();
        if (now - checkedAt > CACHE_MILLIS) {
            onBattery = query();
            checkedAt = now;
        }
        return onBattery;
    }

    private static boolean query() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            return false;
        }
        try {
            Process process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", QUERY)
                    .redirectErrorStream(true).start();
            process.getOutputStream().close();
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            return "1".equals(out);
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
