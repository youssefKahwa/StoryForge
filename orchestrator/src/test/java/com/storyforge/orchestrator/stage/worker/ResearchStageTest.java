package com.storyforge.orchestrator.stage.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Dossier.Claim;
import com.storyforge.orchestrator.model.Dossier.Source;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResearchStageTest {

    private static final Dossier DOSSIER = new Dossier("t",
            List.of(new Source("s1", "A", "https://a.org/1", "A", "press", null),
                    new Source("s2", "B", "https://b.org/1", "B", "press", null)),
            List.of(new Claim("c1", "fixed later", List.of("s1"), true, false),
                    new Claim("c2", "never fixed", List.of("s2"), true, false),
                    new Claim("c3", "fine", List.of("s1", "s2"), true, false)),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

    @Test
    void findsSingleSourcedKeyClaims() {
        assertThat(ResearchStage.weakKeyClaims(DOSSIER)).extracting(Claim::id).containsExactly("c1", "c2");
    }

    @Test
    void mergesNewSourcesWithFreshIdsAndDemotesWhatStaysWeak() {
        ResearchStage.SourceFix fix = new ResearchStage.SourceFix(
                List.of(new Source("s1", "C", "https://c.org/x", "C", "academic", null),
                        new Source("dup", "A again", "https://a.org/1", "A", "press", null)),
                List.of(new ResearchStage.ClaimFix("c1", List.of("s1"))));

        Dossier merged = ResearchStage.merge(DOSSIER, fix);
        Dossier result = ResearchStage.demote(merged, ResearchStage.weakKeyClaims(merged));

        assertThat(result.sources()).hasSize(3);
        assertThat(result.claim("c1").orElseThrow().sourceIds()).containsExactly("s1", "s3");
        assertThat(result.claim("c1").orElseThrow().key()).isTrue();
        Claim c2 = result.claim("c2").orElseThrow();
        assertThat(c2.key()).isFalse();
        assertThat(c2.contested()).isTrue();
        assertThat(ResearchStage.weakKeyClaims(result)).isEmpty();
    }
}
