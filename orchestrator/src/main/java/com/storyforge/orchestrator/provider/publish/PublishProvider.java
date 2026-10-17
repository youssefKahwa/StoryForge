package com.storyforge.orchestrator.provider.publish;

import com.storyforge.orchestrator.model.Publication;

public interface PublishProvider {

    String name();

    Publication publish(PublishRequest request);
}
