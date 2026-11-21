package com.storyforge.orchestrator.run;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class KeepAwakeTest {

    @Test
    void theScriptKeepsItsQuotesAndAskForTheDisplayInDecimal() {
        String script = new String(Base64.getDecoder().decode(KeepAwake.encoded(1234)), StandardCharsets.UTF_16LE);
        // Encoded, the C# quotes survive (as a plain argument they were lost and nothing compiled).
        assertThat(script).contains("[DllImport(\"kernel32.dll\")]");
        // System and display: Modern Standby ignores "system required" alone once the screen turns off.
        assertThat(script).contains("SetThreadExecutionState([uint32]2147483651)");
        assertThat(script).contains("Get-Process -Id 1234");
        assertThat(KeepAwake.FLAGS).isEqualTo(0x80000003L);
    }
}
