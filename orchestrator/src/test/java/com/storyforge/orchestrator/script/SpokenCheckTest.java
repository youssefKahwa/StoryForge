package com.storyforge.orchestrator.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyforge.orchestrator.model.Critique.Flag;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpokenCheckTest {

    /** The kind of prose run 133 had: asides between commas, a colon, a credit list, digits. */
    private static final String ESSAY = """
            ## SCENE 01 | beat: part_1 | duration_target: 32s
            [VOICE: narrator_main] [TONE: playful]
            Every time Tyler Durden walks on screen, my testosterone climbs and my half-grown beard finishes itself.
            Fight Club, David Fincher's 1999 film from Chuck Palahniuk's novel, is one of the best ever made.
            Brad Pitt plays Tyler, Edward Norton plays the numb guy beside him, and Helena Bonham Carter plays Marla.
            Now picture him on a flight: chaotic, self-made, fearless, everything that man forgot he wanted to be.
            """;

    /** The approved prototype: breath groups, connectors, "you", one punch line, delivery marks. */
    private static final String SPOKEN = """
            ## SCENE 01 | beat: part_1 | duration_target: 30s
            [VOICE: narrator_main] [TONE: playful]
            [mood: hype] This guy has the most *dangerous* fan club on the internet. Gym bros quote him, teenagers
            tattoo his face on their arms, and every sigma edit on your feed is basically a love letter to him.
            But nobody tells you this part. The movie is literally *warning* you about him, [beat] and it starts in
            the first ten minutes. So let's break it down, nice and fast.
            ## SCENE 02 | beat: part_2 | duration_target: 30s
            [VOICE: narrator_main] [TONE: playful]
            [mood: neutral] Meet our guy. He has no name, he hasn't slept in six months, and his job is so grey you can
            basically hear the office lights buzzing. So how does he fix it? He shops, a *lot*. He's up at two in the
            morning flipping through furniture catalogs, asking which dining set defines him as a person. And come on,
            man, nobody's dining set defines them. Perfect system, right? [beat] Until *Marla* walks in. She's faking
            it too, and the second he sees her faking, his own fake cure stops working. So you know what happens next.
            """;

    @Test
    void essayProseIsFlaggedWithFixes() {
        SpokenCheck.Result result = SpokenCheck.check(SfmParser.parse(ESSAY));

        assertThat(result.hasMajor()).isTrue();
        assertThat(result.flags()).extracting(Flag::issue)
                .anyMatch(issue -> issue.startsWith("Written-only aside"))
                .anyMatch(issue -> issue.startsWith("Written-only punctuation"))
                .anyMatch(issue -> issue.startsWith("Numbers as digits"));
    }

    @Test
    void theApprovedSpokenStylePasses() {
        SpokenCheck.Result result = SpokenCheck.check(SfmParser.parse(SPOKEN));

        assertThat(result.hasMajor()).as(result.flags().toString()).isFalse();
        assertThat(result.stats().fragmentRuns()).isZero();
        assertThat(result.stats().medianWords()).isBetween(6.0, 16.0);
    }

    @Test
    void aRunOfFragmentsIsRobotRhythm() {
        String robot = """
                ## SCENE 01 | beat: b | duration_target: 20s
                [TONE: playful]
                Meet our guy. No name. No sleep. Zero hours. He works in an office and he hates every minute of it.
                """;

        List<Flag> flags = SpokenCheck.check(SfmParser.parse(robot)).flags();

        assertThat(flags).anyMatch(flag -> flag.issue().startsWith("Robot rhythm") && "major".equals(flag.severity()));
    }

    @Test
    void tooManyBeatsInOneSceneIsMentioned() {
        String beats = """
                ## SCENE 01 | beat: b | duration_target: 20s
                [TONE: tense]
                He opens the door. [beat] Nothing. [beat] Then a sound from upstairs, and you can feel him freeze.
                """;

        assertThat(SpokenCheck.check(SfmParser.parse(beats)).flags())
                .anyMatch(flag -> flag.issue().contains("[beat] marks"));
    }
}
