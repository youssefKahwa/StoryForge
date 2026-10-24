package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.media.MediaException;

/** The free image tier is used up for today; it comes back at 00:00 UTC. */
public class QuotaExhaustedException extends MediaException {

    public QuotaExhaustedException(String message) {
        super(message);
    }
}
