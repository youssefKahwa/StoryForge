package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.model.LicenseRecord;
import com.storyforge.orchestrator.visual.CardRenderer;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "storyforge.providers", name = "image", havingValue = "mock", matchIfMissing = true)
public class MockImageProvider implements ImageProvider {

    @Override
    public String name() {
        return "mock-image";
    }

    @Override
    public ImageResult generate(ImageRequest request, Path outputPng) {
        CardRenderer.placeholder(outputPng, request.width(), request.height(), request.seed(), request.label(),
                request.prompt());
        return new ImageResult(name(), "placeholder", BigDecimal.ZERO, false, LicenseRecord.generated(name()));
    }
}
