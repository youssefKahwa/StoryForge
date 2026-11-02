package com.storyforge.orchestrator.host;

import com.storyforge.orchestrator.model.Alignment;
import com.storyforge.orchestrator.model.ShotList;
import com.storyforge.orchestrator.model.ShotList.Shot;
import com.storyforge.orchestrator.revision.RevisionService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Where the masked host appears: the moments the narrator speaks as a person (an opinion, a question to the viewer,
 * a direct "you", the subscribe line). A few short cutaways, spread out, never in the first seconds (the hook belongs
 * to the strongest footage) and never more than a tenth of the video.
 */
public final class HostMoments {

    /** intent: the kind of gesture that fits what is being said. */
    public record Moment(String shotId, double start, double end, String intent) {
        public double length() {
            return end - start;
        }
    }

    static final double MAX_SHARE = 0.14;
    static final int MAX_MOMENTS = 8;
    static final double MIN_GAP = 30;
    /** The host should be met early, in the opening claim, not only near the end. */
    static final double EARLY = 60;
    static final double NOT_BEFORE = 4;
    static final double MIN_LENGTH = 1.8;
    static final double MAX_LENGTH = 7;

    private static final Pattern FIRST_PERSON = Pattern.compile(
            "\\b(i|i'm|i've|i'd|i'll|my|me|honestly|personally|let me|here's my|i think|i guess)\\b");
    private static final Pattern YOU = Pattern.compile("\\b(you|you're|your|you'd|you'll)\\b");
    private static final Pattern CALL = Pattern.compile("\\b(subscribe|comment|comments|let me know|tell me)\\b");
    private static final Pattern SECRET = Pattern.compile("\\b(secret|truth|hidden|real reason|between us)\\b");
    private static final Pattern TWIST = Pattern.compile("\\b(twist|reveal|actually|realize|realise|shock|insane)\\b");
    private static final Pattern COUNT = Pattern.compile("\\b(first|second|third|one|two|three|number)\\b");
    private static final Pattern THINK = Pattern.compile("\\b(why|wonder|think about|what if|how come|imagine)\\b");
    private static final Pattern COMPARE = Pattern.compile("\\b(compare|versus|vs|than|both|either)\\b");
    private static final Pattern EXCITED = Pattern.compile("\\b(favorite|favourite|love|best|amazing|genius|perfect)\\b");
    private static final Pattern FIGHT = Pattern.compile("\\b(fight|fights|punch|punches|hit|brawl)\\b");
    private static final Pattern DISAGREE = Pattern.compile("\\b(wrong|overrated|disagree|no way|not true)\\b");

    private HostMoments() {
    }

    public static List<Moment> pick(ShotList shots, Alignment alignment, Set<String> excluded) {
        double budget = shots.durationSec() * MAX_SHARE;
        record Candidate(Shot shot, int score, String spoken) {
        }
        List<Candidate> candidates = new ArrayList<>();
        for (Shot shot : shots.shots()) {
            if (shot.graphic() || excluded.contains(shot.id()) || shot.start() < NOT_BEFORE
                    || shot.duration() < MIN_LENGTH || shot.duration() > MAX_LENGTH) {
                continue;
            }
            String spoken = RevisionService.spoken(alignment, shot).toLowerCase(Locale.ROOT);
            int score = score(spoken);
            if (score > 0 && shot.start() < EARLY) {
                score += 2;
            }
            if (score > 0) {
                candidates.add(new Candidate(shot, score, spoken));
            }
        }
        candidates.sort(Comparator.comparingInt(Candidate::score).reversed()
                .thenComparingDouble(c -> c.shot().start()));
        List<Moment> chosen = new ArrayList<>();
        double used = 0;
        for (Candidate candidate : candidates) {
            Shot shot = candidate.shot();
            boolean spaced = chosen.stream().allMatch(m -> Math.abs(m.start() - shot.start()) >= MIN_GAP);
            if (chosen.size() >= MAX_MOMENTS || !spaced || used + shot.duration() > budget) {
                continue;
            }
            chosen.add(new Moment(shot.id(), shot.start(), shot.end(), intent(candidate.spoken())));
            used += shot.duration();
        }
        chosen.sort(Comparator.comparingDouble(Moment::start));
        return chosen;
    }

    static int score(String spoken) {
        int score = 0;
        if (CALL.matcher(spoken).find()) {
            score += 4;
        }
        if (FIRST_PERSON.matcher(spoken).find()) {
            score += 3;
        }
        if (spoken.contains("?") && YOU.matcher(spoken).find()) {
            score += 3;
        } else if (spoken.contains("?")) {
            score += 1;
        } else if (YOU.matcher(spoken).find()) {
            score += 1;
        }
        return score;
    }

    /** The situation a sentence calls for, in the words the owner's Veo clip names use (BP0001_shock_...). */
    static String intent(String spoken) {
        if (CALL.matcher(spoken).find()) {
            return "cta";
        }
        if (SECRET.matcher(spoken).find()) {
            return "secret";
        }
        if (TWIST.matcher(spoken).find()) {
            return "shock";
        }
        if (DISAGREE.matcher(spoken).find()) {
            return "disagree";
        }
        if (FIGHT.matcher(spoken).find()) {
            return "fight";
        }
        if (COMPARE.matcher(spoken).find()) {
            return "compare";
        }
        if (COUNT.matcher(spoken).find()) {
            return "count";
        }
        if (THINK.matcher(spoken).find()) {
            return "realize";
        }
        if (spoken.contains("?")) {
            return "question";
        }
        if (EXCITED.matcher(spoken).find()) {
            return "excited";
        }
        if (FIRST_PERSON.matcher(spoken).find()) {
            return "point";
        }
        return "explain";
    }
}
