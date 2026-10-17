package com.storyforge.orchestrator.provider.image;

import com.storyforge.orchestrator.model.LicenseRecord;
import java.math.BigDecimal;

public record ImageResult(String provider, String model, BigDecimal costUsd, boolean realistic,
                          LicenseRecord license) {
}
