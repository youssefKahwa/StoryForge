package com.storyforge.orchestrator.media;

import java.util.concurrent.TimeUnit;

/**
 * Waiting for an outside program (FFmpeg, Remotion/Chrome, Claude Code, Real-ESRGAN) so that cancelling a run stops
 * it: when the waiting thread is interrupted, the program and everything it started are killed.
 */
public final class Processes {

    private Processes() {
    }

    public static boolean waitFor(Process process, long timeout, TimeUnit unit) throws InterruptedException {
        try {
            return process.waitFor(timeout, unit);
        } catch (InterruptedException e) {
            kill(process);
            throw e;
        }
    }

    /** The program and all its children (a browser's tabs, a compositor). */
    public static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
