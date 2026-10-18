package com.storyforge.orchestrator.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DescriptionBuilderTest {

    @Test
    void stripsResearchNotesFromSourceTitles() {
        assertThat(DescriptionBuilder.cleanTitle("Russian Investigators Are Reopening the Dyatlov Pass Case. But What "
                + "Is It? (published Feb 2019, despite the 2024 date in the URL)"))
                .isEqualTo("Russian Investigators Are Reopening the Dyatlov Pass Case");
        assertThat(DescriptionBuilder.cleanTitle("Mechanisms of slab avalanche release (Gaume & Puzrin, 2021), EPFL "
                + "Infoscience record. I saw it only in search results. The page itself didn't load."))
                .isEqualTo("Mechanisms of slab avalanche release (Gaume & Puzrin, 2021), EPFL Infoscience record");
        assertThat(DescriptionBuilder.cleanTitle("Soldiers of World War I (Imperial War Museum)"))
                .isEqualTo("Soldiers of World War I (Imperial War Museum)");
    }
}
