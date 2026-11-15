package com.storyforge.orchestrator.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.VideoPackage.ScoredTitle;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GrowthResearchTest {

    @Test
    void readsTheSearchBoxAnswerInLowerCase() {
        var node = JsonMapper.builder().build().readTree(
                "[\"fight club\", [\"Fight Club Ending Explained\", \"fight club hindi\", \"fight club tyler\"], [], {}]");
        List<String> phrases = GrowthResearch.parse(node);
        assertThat(phrases).containsExactly("fight club ending explained", "fight club hindi", "fight club tyler");
        assertThat(phrases.stream().filter(GrowthResearch::useful).toList())
                .containsExactly("fight club ending explained", "fight club tyler");
    }

    @Test
    void dropsPirateAndOtherLanguageSuggestions() {
        assertThat(GrowthResearch.useful("fight club full movie")).isFalse();
        assertThat(GrowthResearch.useful("fight club soundtrack")).isFalse();
        assertThat(GrowthResearch.useful("fight club tamil dubbed")).isFalse();
        assertThat(GrowthResearch.useful("fight club explained")).isTrue();
        assertThat(GrowthResearch.useful(" ")).isFalse();
    }

    @Test
    void theSearchedNameIsShortAndPlain() {
        assertThat(GrowthResearch.name(null, "Fight Club: Why Tyler Is Not a Role Model")).isEqualTo("Fight Club");
        assertThat(GrowthResearch.name(null, "Se7en (1995) - the ending")).isEqualTo("Se7en");
        assertThat(GrowthResearch.name(null, null)).isEmpty();
    }

    @Test
    void aTitleThatUsesARealSearchRanksHigherAndSaysWhy() {
        List<String> searches = List.of("fight club ending explained", "tyler durden", "fight club");
        List<ScoredTitle> top = TitleScorer.top(List.of(
                "The Detail Everyone Missed in This Film",
                "Fight Club Ending Explained Like Never Before"), "Fight Club", 2, searches);
        assertThat(top.get(0).title()).isEqualTo("Fight Club Ending Explained Like Never Before");
        assertThat(top.get(0).reasons()).anyMatch(r -> r.contains("fight club ending explained"));
        // The longest phrase wins; one word is never a "phrase".
        assertThat(TitleScorer.searchedPhrase("Fight Club Ending Explained", searches))
                .isEqualTo("fight club ending explained");
        assertThat(TitleScorer.searchedPhrase("Tyler", List.of("tyler"))).isNull();
    }
}
