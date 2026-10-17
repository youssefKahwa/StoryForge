package com.storyforge.orchestrator.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public record AssetManifest(boolean realisticImagery, List<Asset> assets) {

    public record Asset(String shotId, String file, String kind, String provider, String model, String prompt,
                        long seed, BigDecimal costUsd, boolean realistic, LicenseRecord license) {
    }

    public Optional<Asset> forShot(String shotId) {
        return assets.stream().filter(asset -> asset.shotId().equals(shotId)).findFirst();
    }
}
