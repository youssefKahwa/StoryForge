package com.storyforge.orchestrator.provider.realmedia;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class OpenverseHitTest {

    private static JsonNode hit(String license, int width) {
        return JsonMapper.builder().build().readTree("""
                {"title": "Abandoned house field night", "url": "https://images.rawpixel.com/x.jpg",
                 "foreign_landing_url": "https://www.rawpixel.com/image/1", "creator": "", "source": "rawpixel",
                 "license": "%s", "license_version": "4.0", "width": %d, "mature": false,
                 "attribution": "\\"Abandoned house field night\\" is marked with CC0 1.0.",
                 "tags": [{"name": "cabin"}, {"name": "field night"}]}
                """.formatted(license, width));
    }

    @Test
    void onlyLicencesAMonetizedVideoMayUseAreKept() {
        assertThat(RealMediaFinder.OpenverseHit.of(hit("cc0", 4800))).isPresent();
        assertThat(RealMediaFinder.OpenverseHit.of(hit("by", 4800))).isPresent();
        assertThat(RealMediaFinder.OpenverseHit.of(hit("pdm", 4800))).isPresent();
        // Share-alike would bind the whole video, non-commercial forbids monetizing it, no-derivatives forbids the crop.
        assertThat(RealMediaFinder.OpenverseHit.of(hit("by-sa", 4800))).isEmpty();
        assertThat(RealMediaFinder.OpenverseHit.of(hit("by-nc", 4800))).isEmpty();
        assertThat(RealMediaFinder.OpenverseHit.of(hit("by-nd", 4800))).isEmpty();
        assertThat(RealMediaFinder.OpenverseHit.of(hit("cc0", 640))).isEmpty();
    }

    @Test
    void theCreditAndLicenceTypeFollowTheLicence() {
        var cc0 = RealMediaFinder.OpenverseHit.of(hit("cc0", 4800)).orElseThrow();
        assertThat(cc0.licenseType()).isEqualTo("cc0");
        assertThat(cc0.creator()).isEqualTo("unknown author");
        assertThat(cc0.tags()).isEqualTo("cabin field night");
        var by = RealMediaFinder.OpenverseHit.of(hit("by", 4800)).orElseThrow();
        assertThat(by.licenseType()).isEqualTo("cc-by");
        assertThat(by.licenseLabel()).isEqualTo("CC BY 4.0");
        assertThat(RealMediaFinder.OpenverseHit.of(hit("pdm", 4800)).orElseThrow().licenseType())
                .isEqualTo("public_domain");
    }
}
