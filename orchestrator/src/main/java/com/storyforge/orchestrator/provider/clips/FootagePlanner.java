package com.storyforge.orchestrator.provider.clips;

import com.storyforge.orchestrator.provider.clips.FootageIndex.Segment;
import com.storyforge.orchestrator.sound.BeatGrid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Decides which moment plays on which shot. Walks the shots in time order and takes the best pick that passes the
 * rules; the editor's ranking decides inside each step:
 * <ol>
 *   <li>unused, clean, and not the same set-up as the shot next to it;</li>
 *   <li>unused and clean;</li>
 *   <li>unused, even if dark or from a low-quality copy;</li>
 *   <li>second use, clean, not the same set-up as the shot next to it;</li>
 *   <li>second use.</li>
 * </ol>
 * A moment is never used more than {@code maxUses} times, and a second use is at least {@link #MIN_REUSE_GAP} seconds
 * from every other use. Everything that goes on screen counts: extra moments and follow-on moments too.
 */
final class FootagePlanner {

    /** A video shot to cover. Its clip runs a little longer than the shot, for the transition. */
    record Need(String id, double start, double end) {
        double target() {
            return end - start + HANDLE;
        }
    }

    /** One ranked pick: a catalogue moment, and optional extra moments that stay on the same idea. */
    public record Pick(Integer tile, List<Integer> more) {
    }

    /** {@code seconds} of a segment from {@code from} in its source. */
    record Part(Segment segment, double from, double seconds) {
    }

    record Choice(Need need, List<Part> parts, int step) {
        double seconds() {
            return parts.stream().mapToDouble(Part::seconds).sum();
        }

        List<Integer> moments() {
            return parts.stream().map(p -> p.segment().moment()).toList();
        }
    }

    static final double HANDLE = 0.6;
    /** The slowest a clip may play, so a chain this much shorter than its shot still covers it. */
    static final double SLOWEST = 0.8;
    /** Most library moments are 1.5-3 s, so a long shot is a quick montage of a few of them, the recap rhythm. */
    static final int MAX_MOMENTS = 5;
    static final double MIN_REUSE_GAP = 90;
    /** A cut inside a montage moves this far at most to land on a beat; every piece keeps at least MIN_PIECE. */
    static final double BEAT_TOLERANCE = 0.4;
    static final double MIN_PIECE = 0.8;
    private static final double EDGE = 0.05;
    private static final double NEXT_TO = 1;
    private static final int UNUSED_CLEAN_VARIED = 0;
    private static final int UNUSED_CLEAN = 1;
    private static final int UNUSED_ANY = 2;
    private static final int SECOND_CLEAN_VARIED = 3;
    private static final int SECOND_ANY = 4;
    static final int CONTINUATION = 5;

    private final List<Segment> segments;
    private final int maxUses;
    private final List<Double> beats;
    private final Map<Integer, Segment> byTile = new HashMap<>();
    private final Map<Integer, Segment> bestOf = new HashMap<>();
    /** The best copy of every moment of a set-up, longest first. */
    private final Map<Integer, List<Segment>> setUps = new HashMap<>();
    private final Map<Integer, Integer> position = new HashMap<>();
    private final Map<Integer, List<Double>> uses = new HashMap<>();
    /** How far into a moment earlier uses went, so a second use can show a later part of it. */
    private final Map<Integer, Double> consumed = new HashMap<>();
    private final Set<Integer> blocked = new HashSet<>();
    private final Set<Integer> blockedForShot = new HashSet<>();
    private final List<Choice> placed = new ArrayList<>();
    private String currentShot;

    /** {@code segments}: the usable footage, in source order. */
    FootagePlanner(List<Segment> segments, int maxUses) {
        this(segments, maxUses, List.of());
    }

    /** {@code beats}: the music's beats in video time, sorted; cuts inside a montage move onto them. */
    FootagePlanner(List<Segment> segments, int maxUses, List<Double> beats) {
        this.segments = segments;
        this.maxUses = maxUses;
        this.beats = beats;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            byTile.put(segment.tile(), segment);
            position.put(segment.tile(), i);
            if (segment.best() || !bestOf.containsKey(segment.moment())) {
                bestOf.put(segment.moment(), segment);
            }
        }
        bestOf.values().forEach(s -> setUps.computeIfAbsent(s.family(), f -> new ArrayList<>()).add(s));
        setUps.values().forEach(list -> list.sort((a, b) -> Double.compare(b.length(), a.length())));
    }

    /** The best chain for this shot from the editor's ranked picks, or empty if nothing passes. */
    Optional<Choice> choose(Need need, List<Pick> picks) {
        enter(need);
        for (int step = UNUSED_CLEAN_VARIED; step <= SECOND_ANY; step++) {
            for (Pick pick : picks) {
                List<Segment> chain = chain(need, pick, picks, step);
                if (!chain.isEmpty()) {
                    Optional<Choice> choice = choice(need, chain, step);
                    if (choice.isPresent()) {
                        return choice;
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** For a shot with no pick: the previous shot's scene simply goes on, with moments not used yet. */
    Optional<Choice> continuation(Need need) {
        enter(need);
        Choice before = null;
        for (Choice choice : placed) {
            if (Math.abs(choice.need().end() - need.start()) <= NEXT_TO) {
                before = choice;
            }
        }
        if (before == null) {
            return Optional.empty();
        }
        List<Segment> chain = new ArrayList<>();
        Segment last = before.parts().get(before.parts().size() - 1).segment();
        extend(chain, last, need, UNUSED_ANY);
        return chain.isEmpty() ? Optional.empty() : choice(need, chain, CONTINUATION);
    }

    void commit(Choice choice) {
        for (Part part : choice.parts()) {
            int moment = part.segment().moment();
            uses.computeIfAbsent(moment, m -> new ArrayList<>()).add(choice.need().start());
            consumed.merge(moment, part.from() + part.seconds() - part.segment().start(), Math::max);
        }
        placed.add(choice);
    }

    /**
     * The rendered clip was unusable (too dark on screen). Offered no more for this shot; for any shot when the
     * culprit is certain: the only moment in the clip, or one the index already calls dark.
     */
    void reject(Choice choice) {
        for (Part part : choice.parts()) {
            if (choice.parts().size() == 1 || part.segment().dark()) {
                blocked.add(part.segment().moment());
            }
            blockedForShot.add(part.segment().moment());
        }
    }

    boolean used(int moment) {
        return uses.containsKey(moment);
    }

    Map<Integer, List<Double>> uses() {
        return uses;
    }

    List<Choice> placed() {
        return placed;
    }

    private void enter(Need need) {
        if (!need.id().equals(currentShot)) {
            currentShot = need.id();
            blockedForShot.clear();
        }
    }

    /**
     * The pick, then the editor's extra moments, then the shot's other picks, then other takes of the same set-ups
     * (the same character in the same place fits the words as well), then what follows in the source.
     */
    private List<Segment> chain(Need need, Pick pick, List<Pick> all, int step) {
        Segment first = copy(pick.tile());
        if (first == null || !passes(first, need, step)) {
            return List.of();
        }
        if ((step == UNUSED_CLEAN_VARIED || step == SECOND_CLEAN_VARIED) && sameSetUpNextTo(first, need)) {
            return List.of();
        }
        List<Segment> chain = new ArrayList<>(List.of(first));
        for (Integer extra : pick.more() == null ? List.<Integer>of() : pick.more()) {
            add(chain, copy(extra), need, step);
        }
        for (Pick other : all) {
            if (other != pick) {
                add(chain, copy(other.tile()), need, step);
            }
        }
        for (Segment chosen : List.copyOf(chain)) {
            for (Segment take : setUps.getOrDefault(chosen.family(), List.of())) {
                add(chain, take, need, step);
            }
        }
        for (Segment chosen : List.copyOf(chain)) {
            extend(chain, chosen, need, step);
        }
        return chain;
    }

    private void add(List<Segment> chain, Segment segment, Need need, int step) {
        if (segment != null && chain.size() < MAX_MOMENTS && room(chain) < need.target()
                && chain.stream().noneMatch(s -> s.moment() == segment.moment()) && passes(segment, need, step)) {
            chain.add(segment);
        }
    }

    /** Back-to-back cuts that follow {@code last} in its source, while they pass the rules. */
    private void extend(List<Segment> chain, Segment last, Need need, int step) {
        Segment next = next(last);
        while (next != null && chain.size() < MAX_MOMENTS && room(chain) < need.target() && passes(next, need, step)) {
            Segment moment = next;
            if (chain.stream().anyMatch(s -> s.moment() == moment.moment())) {
                break;
            }
            chain.add(next);
            next = next(next);
        }
    }

    private Segment next(Segment segment) {
        int index = position.get(segment.tile()) + 1;
        if (index >= segments.size()) {
            return null;
        }
        Segment next = segments.get(index);
        return next.source() == segment.source() && Math.abs(next.start() - segment.end()) <= 0.15 ? next : null;
    }

    private static double room(Segment segment) {
        return Math.max(0, segment.length() - 2 * EDGE);
    }

    private static double room(List<Segment> chain) {
        return chain.stream().mapToDouble(FootagePlanner::room).sum();
    }

    private Segment copy(Integer tile) {
        Segment segment = tile == null ? null : byTile.get(tile);
        return segment == null ? null : bestOf.get(segment.moment());
    }

    private boolean passes(Segment segment, Need need, int step) {
        int moment = segment.moment();
        if (blocked.contains(moment) || blockedForShot.contains(moment)) {
            return false;
        }
        List<Double> times = uses.getOrDefault(moment, List.of());
        if (times.size() >= maxUses || (step <= UNUSED_ANY && !times.isEmpty())) {
            return false;
        }
        if (times.stream().anyMatch(t -> Math.abs(t - need.start()) < MIN_REUSE_GAP)) {
            return false;
        }
        boolean mustBeClean = step == UNUSED_CLEAN_VARIED || step == UNUSED_CLEAN || step == SECOND_CLEAN_VARIED;
        return !mustBeClean || segment.clean();
    }

    private boolean sameSetUpNextTo(Segment segment, Need need) {
        for (Choice choice : placed) {
            List<Part> parts = choice.parts();
            if (Math.abs(choice.need().end() - need.start()) <= NEXT_TO
                    && parts.get(parts.size() - 1).segment().family() == segment.family()) {
                return true;
            }
            if (Math.abs(need.end() - choice.need().start()) <= NEXT_TO
                    && parts.get(0).segment().family() == segment.family()) {
                return true;
            }
        }
        return false;
    }

    /** Turns a chain into exact cuts, or nothing if it cannot cover the shot even played at the slowest speed. */
    private Optional<Choice> choice(Need need, List<Segment> chain, int step) {
        double target = need.target();
        double total = chain.stream().mapToDouble(FootagePlanner::room).sum();
        if (total < target * SLOWEST) {
            return Optional.empty();
        }
        // Every moment gets its share of the shot, so each cut inside keeps a little room to move onto a beat.
        double ratio = Math.min(1, target / total);
        List<Part> parts = new ArrayList<>();
        for (Segment segment : chain) {
            double room = room(segment);
            double take = room * ratio;
            // A second use starts as late as still fits, so it does not show the same frames again.
            double skip = Math.min(consumed.getOrDefault(segment.moment(), 0.0), room - take);
            parts.add(new Part(segment, segment.start() + EDGE + Math.max(0, skip), take));
        }
        return Optional.of(new Choice(need, total >= target ? onTheBeat(need, parts) : parts, step));
    }

    /**
     * The cuts between the moments of a montage, moved onto the nearest beat when it is close and every piece stays
     * long enough and inside its moment. Only for a clip that plays at normal speed (its times are video times).
     */
    List<Part> onTheBeat(Need need, List<Part> parts) {
        if (beats.isEmpty() || parts.size() < 2) {
            return parts;
        }
        List<Part> out = new ArrayList<>(parts);
        double at = need.start();
        for (int k = 0; k + 1 < out.size(); k++) {
            Part left = out.get(k);
            Part right = out.get(k + 1);
            double cut = at + left.seconds();
            Double beat = BeatGrid.nearest(beats, cut, BEAT_TOLERANCE);
            if (beat != null) {
                double shift = beat - cut;
                double leftSeconds = left.seconds() + shift;
                double rightSeconds = right.seconds() - shift;
                double leftEnd = left.segment().end() - EDGE;
                double rightEnd = right.segment().end() - EDGE;
                if (leftSeconds >= MIN_PIECE && rightSeconds >= MIN_PIECE && left.from() + leftSeconds <= leftEnd
                        && right.from() + rightSeconds <= rightEnd) {
                    out.set(k, new Part(left.segment(), left.from(), leftSeconds));
                    out.set(k + 1, new Part(right.segment(), right.from(), rightSeconds));
                }
            }
            at += out.get(k).seconds();
        }
        return out;
    }

    /** Video times of the cuts between the moments of a clip that starts at {@code start}. */
    static List<Double> cuts(double start, List<Part> parts) {
        List<Double> out = new ArrayList<>();
        double at = start;
        for (int k = 0; k + 1 < parts.size(); k++) {
            at += parts.get(k).seconds();
            out.add(Math.round(at * 1000) / 1000.0);
        }
        return out;
    }
}
