package com.storyforge.orchestrator.inspiration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class MainVideoRuleTest {

    @Test
    void theFirstLinkIsTheMainVideoWhenNoneIsGiven() {
        List<String> links = List.of("https://youtu.be/a", "https://youtu.be/b");
        String main = InspirationService.mainUrl(null, links);
        assertThat(main).isEqualTo("https://youtu.be/a");
        assertThat(InspirationService.ordered(main, links)).containsExactly("https://youtu.be/a", "https://youtu.be/b");
    }

    @Test
    void aGivenMainVideoLeadsAndIsNotRepeated() {
        List<String> links = List.of("https://youtu.be/a", "https://youtu.be/m");
        String main = InspirationService.mainUrl(" https://youtu.be/m ", links);
        assertThat(main).isEqualTo("https://youtu.be/m");
        assertThat(InspirationService.ordered(main, links)).containsExactly("https://youtu.be/m", "https://youtu.be/a");
    }

    @Test
    void noLinksMeansNoMainVideo() {
        assertThat(InspirationService.mainUrl("  ", List.of())).isNull();
        assertThat(InspirationService.ordered(null, List.of())).isEmpty();
    }
}
