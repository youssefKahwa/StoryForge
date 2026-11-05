package com.storyforge.orchestrator.script;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpeechTextTest {

    @Test
    void yearsAreSaidLikeYears() {
        assertThat(SpeechText.normalize("Fight Club came out in 1999.")).isEqualTo(
                "Fight Club came out in nineteen ninety-nine.");
        assertThat(SpeechText.normalize("in 2005 and 2024")).isEqualTo("in two thousand five and twenty twenty-four");
        assertThat(SpeechText.normalize("in 1900")).isEqualTo("in nineteen hundred");
        assertThat(SpeechText.normalize("in 1905")).isEqualTo("in nineteen oh five");
    }

    @Test
    void moneyPercentAndBigNumbers() {
        assertThat(SpeechText.normalize("It made $37M.")).isEqualTo("It made thirty-seven million dollars.");
        assertThat(SpeechText.normalize("about 40% of people")).isEqualTo("about forty percent of people");
        assertThat(SpeechText.normalize("6,000,000 copies")).isEqualTo("six million copies");
        assertThat(SpeechText.normalize("it took 3.5 hours")).isEqualTo("it took three point five hours");
    }

    @Test
    void decadesOrdinalsAndAbbreviations() {
        assertThat(SpeechText.normalize("the 1990s")).isEqualTo("the nineteen nineties");
        assertThat(SpeechText.normalize("the 21st night")).isEqualTo("the twenty-first night");
        assertThat(SpeechText.normalize("Mr. Durden vs. the system & you")).isEqualTo(
                "Mister Durden versus the system and you");
    }

    @Test
    void wordsAreLeftAlone() {
        String spoken = "So how does he fix it? He shops, a lot.";
        assertThat(SpeechText.normalize(spoken)).isEqualTo(spoken);
    }
}
