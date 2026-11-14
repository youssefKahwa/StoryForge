package com.storyforge.orchestrator.script;

import com.storyforge.orchestrator.model.Critique.Flag;
import com.storyforge.orchestrator.model.Scene;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Is the script written to be spoken? The measurable half of the "written for the ear" rules (prompts/script.md),
 * checked on every script with no AI call. A narration reads like an AI reading a script when it is essay prose (long
 * sentences, asides between commas, colons, lists of names) or when it is a chain of fragments (every full stop is a
 * stop and a falling tone: our old recap stopped every 0.9 s). People talk in breath groups, link ideas with "and /
 * so / but", and talk to "you". Major findings fail the critique round, so the existing rewrite pass fixes them.
 */
public final class SpokenCheck {

    private static final Pattern SENTENCE = Pattern.compile("[^.?!]+(?:\\.\\.\\.|[.?!]+)?");
    /** "X, <an aside>, is ...": the aside between commas sits between a subject and its verb. */
    private static final Pattern ASIDE = Pattern.compile(
            ",\\s+[^,.?!:;]{4,70},\\s+(?:is|was|are|were|has|had|became|remains|stays|gets|turned)\\b",
            Pattern.CASE_INSENSITIVE);
    /** Two capitalised words in a row ("Edward Norton"): three in one sentence is a credit list. */
    private static final Pattern FULL_NAME = Pattern.compile("\\b[A-Z][a-z]+\\s+[A-Z][a-z]+");
    private static final Pattern WRITTEN_ONLY = Pattern.compile("[;:()\\[\\]/]");
    private static final Pattern DIGITS = Pattern.compile("\\d");
    private static final Pattern YOU = Pattern.compile("\\b(you|your|you're|you'd|you'll|you've)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> CONNECTORS = Set.of("so", "and", "but", "now", "okay", "see", "because", "which",
            "look", "then", "or", "yeah", "well", "still", "until", "plus");
    private static final int FRAGMENT_WORDS = 3;
    private static final int LONG_WORDS = 30;
    /**
     * Kinds of ask to the viewer. One at the end is a call to action; two or more ("like, subscribe, and follow me")
     * is a list read out at the moment the viewer decides to leave.
     */
    private static final List<Pattern> ASKS = List.of(
            Pattern.compile("\\b(?:hit|smash|leave|drop|give) (?:a |the |that |it a )?like\\b|\\blike (?:this|the) video\\b"
                    + "|\\blike(?:,| and) (?:subscribe|share|comment)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsubscrib(?:e|ing)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bfollow (?:me|us|the channel)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bcomments?\\b|\\bcomment below\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bshare (?:this|it|the video)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:the )?bell\\b|\\bnotifications?\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(?:watch|check out|click) (?:this|that|my|the) (?:next )?(?:video|one)\\b"
                    + "|\\bnext video\\b", Pattern.CASE_INSENSITIVE));
    /** Closers that tell the viewer the video is over, so they leave before the last line. */
    private static final Pattern CLOSER = Pattern.compile("\\b(?:thanks|thank you)(?: so much)? for watching\\b"
            + "|\\bin conclusion\\b|\\bto sum (?:it )?up\\b|\\bthat'?s (?:it|all) for (?:today|this video)\\b",
            Pattern.CASE_INSENSITIVE);

    private SpokenCheck() {
    }

    public record Stats(int sentences, double medianWords, double fragmentShare, int fragmentRuns, int longSentences,
                        double connectorShare, double youPer100) {
    }

    public record Result(Stats stats, List<Flag> flags) {

        public boolean hasMajor() {
            return flags.stream().anyMatch(flag -> "major".equals(flag.severity()));
        }
    }

    public static Result check(List<Scene> scenes) {
        List<Flag> flags = new ArrayList<>();
        List<Integer> lengths = new ArrayList<>();
        int fragments = 0;
        int runs = 0;
        int longOnes = 0;
        int connectors = 0;
        int words = 0;
        int you = 0;
        for (Scene scene : scenes) {
            String text = scene.narration() == null ? "" : scene.narration();
            int run = 0;
            boolean flaggedRun = false;
            for (String sentence : sentences(text)) {
                String[] tokens = sentence.split("\\s+");
                int n = tokens.length;
                lengths.add(n);
                words += n;
                if (n <= FRAGMENT_WORDS) {
                    fragments++;
                    run++;
                    if (run == 3) {
                        runs++;
                        if (!flaggedRun) {
                            flags.add(new Flag(scene.id(), "major", "Robot rhythm: three short fragments in a row (\""
                                    + sentence + "\"). Join them into one spoken sentence with \"and\" or commas, and "
                                    + "keep one short punch line at most.", false));
                            flaggedRun = true;
                        }
                    }
                } else {
                    run = 0;
                }
                if (n > LONG_WORDS) {
                    longOnes++;
                    flags.add(new Flag(scene.id(), "minor", "Too long to say in one breath (" + n + " words): \""
                            + shorten(sentence) + "\". Split it where you would breathe.", false));
                }
                Matcher names = FULL_NAME.matcher(sentence);
                int nameCount = 0;
                while (names.find()) {
                    nameCount++;
                }
                if (nameCount >= 3) {
                    flags.add(new Flag(scene.id(), "minor", "A list of names in one sentence (\"" + shorten(sentence)
                            + "\"): a listener keeps one or two. Give each name its moment, or keep the one that "
                            + "matters.", false));
                }
                String first = tokens[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z']", "");
                if (CONNECTORS.contains(first)) {
                    connectors++;
                }
            }
            Matcher you1 = YOU.matcher(text);
            while (you1.find()) {
                you++;
            }
            Matcher aside = ASIDE.matcher(text);
            if (aside.find()) {
                flags.add(new Flag(scene.id(), "major", "Written-only aside between commas (\"" + aside.group().strip()
                        + "\"): a listener loses the sentence. Say it as its own sentence.", false));
            }
            Matcher written = WRITTEN_ONLY.matcher(text);
            if (written.find()) {
                flags.add(new Flag(scene.id(), "major", "Written-only punctuation \"" + written.group() + "\" in the "
                        + "narration: no colons, semicolons, brackets or slashes in speech. Use a full stop, a comma or "
                        + "\"and\".", false));
            }
            if (DIGITS.matcher(text).find()) {
                flags.add(new Flag(scene.id(), "minor", "Numbers as digits: write them the way they are said "
                        + "(\"nineteen ninety-nine\", \"six million\").", false));
            }
            long beats = scene.segments().stream().filter(s -> "beat".equals(s.kind())).count();
            long moods = scene.segments().stream().filter(s -> "mood".equals(s.kind())).count();
            if (beats > 1) {
                flags.add(new Flag(scene.id(), "minor", beats + " [beat] marks: keep one held pause per scene, before "
                        + "the real reveal.", false));
            }
            if (moods > 3) {
                flags.add(new Flag(scene.id(), "minor", moods + " [mood] switches: at most three per scene.", false));
            }
        }
        int sentences = lengths.size();
        double median = median(lengths);
        double fragmentShare = sentences == 0 ? 0 : fragments / (double) sentences;
        double connectorShare = sentences == 0 ? 0 : connectors / (double) sentences;
        double youPer100 = words == 0 ? 0 : 100.0 * you / words;
        if (sentences >= 12) {
            if (median > 16) {
                flags.add(new Flag("all", "major", "Essay rhythm: the median sentence is " + Math.round(median)
                        + " words. Talk in breath groups: most sentences 8 to 18 words, one idea each.", false));
            }
            if (fragmentShare > 0.3) {
                flags.add(new Flag("all", "major", Math.round(fragmentShare * 100) + "% of the sentences are fragments "
                        + "of three words or less: it sounds like a list being read. Keep punch lines rare.", false));
            }
            if (connectorShare < 0.12) {
                flags.add(new Flag("all", "minor", "Few sentences start like talk (" + Math.round(connectorShare * 100)
                        + "%): about one in three can start with So, And, But, Now, Because, Which means.", false));
            }
            if (youPer100 < 1.0) {
                flags.add(new Flag("all", "minor", "The viewer is rarely spoken to (\"you\" " + Math.round(youPer100
                        * 10) / 10.0 + " times per 100 words): talk to one person.", false));
            }
        }
        if (!scenes.isEmpty()) {
            ending(scenes.get(scenes.size() - 1), flags);
        }
        Stats stats = new Stats(sentences, median, round(fragmentShare), runs, longOnes, round(connectorShare),
                round(youPer100));
        return new Result(stats, List.copyOf(flags));
    }

    /** The last scene ends on one ask, and never announces that the video is over. */
    private static void ending(Scene last, List<Flag> flags) {
        String text = last.narration() == null ? "" : last.narration();
        long asks = ASKS.stream().filter(ask -> ask.matcher(text).find()).count();
        if (asks >= 2) {
            flags.add(new Flag(last.id(), "major", asks + " different asks in the last scene: end with ONE ask, a "
                    + "question for the comments or a related video to watch next. The subscribe button is on screen "
                    + "already.", false));
        }
        Matcher closer = CLOSER.matcher(text);
        if (closer.find()) {
            flags.add(new Flag(last.id(), "major", "\"" + closer.group() + "\" tells the viewer the video is over, so "
                    + "they leave before the last line: go from the last point straight into the ask.", false));
        }
    }

    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = SENTENCE.matcher(text);
        while (m.find()) {
            String s = m.group().strip();
            if (s.codePoints().anyMatch(Character::isLetterOrDigit)) {
                out.add(s);
            }
        }
        return out;
    }

    private static double median(List<Integer> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Integer> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
    }

    private static String shorten(String sentence) {
        return sentence.length() <= 70 ? sentence : sentence.substring(0, 67) + "...";
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
