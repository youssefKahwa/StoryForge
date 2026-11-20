package com.storyforge.orchestrator.model;

import java.util.List;
import java.util.Map;

/**
 * An animated explainer drawn by render-svc's motion kit, in the channel's colors, instead of a plain text card.
 * Claude picks the template and writes the words; each item appears when the narrator says it ({@code at}, seconds
 * in the video, absolute so moving a cut onto the beat changes nothing).
 *
 * <ul>
 *   <li>versus: 2 items, two sides; detail = the lines under each side, split by " | ".</li>
 *   <li>chain: 3-5 items, causes and effects joined by arrows.</li>
 *   <li>list: 3-5 items, numbered rules or points; detail "check" or "cross" draws a mark.</li>
 *   <li>stat: 1-2 items; label = the number ("6", "$63M"), detail = what it counts.</li>
 *   <li>timeline: 3-6 items; label = the date, detail = what happened.</li>
 *   <li>evidence: 3-6 items, clue cards on a board joined by red string (the twist's payoff).</li>
 *   <li>quote: 1 item; label = the words, detail = who said them.</li>
 *   <li>merge: 3 items; the first two slide into one and become the third.</li>
 * </ul>
 */
public record MotionGraphic(String template, String title, List<Item> items, String accent) {

    public record Item(String label, String detail, String cue, Double at) {
    }

    /** Template -> {min items, max items}. */
    public static final Map<String, int[]> TEMPLATES = Map.of(
            "versus", new int[] {2, 2},
            "chain", new int[] {3, 5},
            "list", new int[] {3, 5},
            "stat", new int[] {1, 2},
            "timeline", new int[] {3, 6},
            "evidence", new int[] {3, 6},
            "quote", new int[] {1, 1},
            "merge", new int[] {3, 3});

    public static final int MAX_LABEL = 28;
    public static final int MAX_QUOTE = 140;
    public static final int MAX_DETAIL = 48;
    public static final int MAX_TITLE = 32;
}
