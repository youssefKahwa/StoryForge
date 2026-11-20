package com.storyforge.orchestrator.sound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SoundPacksTest {

    private static SoundPacks.Entry entry(String path, double seconds) {
        return new SoundPacks.Entry("earshot", "C:/packs/earshot/" + path, path, SoundPacks.keywords(path), seconds,
                null, 0, -20, -3);
    }

    @Test
    void wordsComeFromTheFoldersAndTheFileName() {
        assertThat(SoundPacks.keywords("Whooshes/WhooshAir_Fast 03"))
                .contains("whooshes", "whoosh", "air", "fast")
                .doesNotContain("03");
        assertThat(SoundPacks.keywords("SFX/Earshot Clicks/click_take2")).contains("clicks", "click")
                .doesNotContain("sfx", "earshot", "take");
    }

    @Test
    void aCategoryFindsItsSoundsByTheirNamesAndLength() {
        List<SoundPacks.Entry> pack = List.of(
                entry("Whooshes/Whoosh Air Fast", 0.8),
                entry("Whooshes/Whoosh Long Drone", 12),
                entry("Hits/Metal Hit", 1.2),
                entry("Hums/Electric Hum", 3));
        List<SoundPacks.Entry> whooshes = SoundPacks.rank(pack, SoundPacks.WORDS.get("whoosh_fast"), 0.15, 1.2);
        assertThat(whooshes).extracting(SoundPacks.Entry::name).containsExactly("Whooshes/Whoosh Air Fast");
        assertThat(SoundPacks.rank(pack, SoundPacks.WORDS.get("impact"), 0.5, 4))
                .extracting(SoundPacks.Entry::name).containsExactly("Hits/Metal Hit");
        assertThat(SoundPacks.rank(pack, SoundPacks.WORDS.get("bell"), 0.3, 3)).isEmpty();
    }

    @Test
    void aPackSoundCarriesThePacksLicence() {
        var info = new SoundPacks.PackInfo(true, "Earshot SFX", "royalty-free, commercial use allowed",
                "Earshot SFX by Paul Urmson");
        var license = SoundPacks.license(entry("Hits/Metal Hit", 1.2), info);
        assertThat(license.complete()).isTrue();
        assertThat(license.forbidden()).isFalse();
        assertThat(license.evidence()).contains("Earshot SFX by Paul Urmson").contains("commercial use allowed");
    }
}
