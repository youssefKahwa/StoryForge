package com.storyforge.orchestrator.script;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words a voice actually says. The script is asked to write numbers and symbols as spoken words; this is the
 * safety net for what slips through: "$37M" becomes "37 million dollars", "40%" "40 percent", "1999" "nineteen
 * ninety-nine", "6,000" "six thousand", "1990s" "nineteen nineties", "21st" "twenty-first", "Mr." "Mister". Only the
 * text sent to the voice changes; captions keep the written words.
 */
public final class SpeechText {

    private static final String[] ONES = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
            "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
            "nineteen"};
    private static final String[] TENS = {"", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty",
            "ninety"};
    private static final String[][] ABBREVIATIONS = {
            {"\\bMr\\.", "Mister"}, {"\\bMrs\\.", "Missus"}, {"\\bDr\\.", "Doctor"}, {"\\bvs\\.?(?=\\s)", "versus"},
            {"\\betc\\.", "et cetera"}, {"\\be\\.g\\.", "for example"}, {"\\bi\\.e\\.", "that is"}, {"&", "and"}};
    private static final Pattern MONEY = Pattern.compile("\\$\\s?(\\d[\\d,]*(?:\\.\\d+)?)\\s?(k|K|M|B|bn|million|billion|thousand)?\\b");
    private static final Pattern PERCENT = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s?%");
    private static final Pattern DECADE = Pattern.compile("\\b(1[1-9]|20)(\\d)0s\\b");
    private static final Pattern ORDINAL = Pattern.compile("\\b(\\d+)(st|nd|rd|th)\\b");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\w.])(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d+))?(?![\\w])");

    private SpeechText() {
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String out = text;
        for (String[] abbreviation : ABBREVIATIONS) {
            out = out.replaceAll(abbreviation[0], abbreviation[1]);
        }
        out = replace(MONEY, out, m -> m.group(1) + (m.group(2) == null ? "" : " " + scale(m.group(2))) + " dollars");
        out = replace(PERCENT, out, m -> m.group(1) + " percent");
        out = replace(DECADE, out, m -> decade(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        out = replace(ORDINAL, out, m -> ordinal(Long.parseLong(m.group(1))));
        out = replace(NUMBER, out, m -> {
            long whole = Long.parseLong(m.group(1).replace(",", ""));
            String said = m.group(1).length() == 4 && m.group(2) == null && whole >= 1100 && whole <= 2099
                    && !m.group(1).contains(",") ? year((int) whole) : words(whole);
            if (m.group(2) != null) {
                StringBuilder digits = new StringBuilder();
                for (char c : m.group(2).toCharArray()) {
                    digits.append(' ').append(ONES[c - '0']);
                }
                said += " point" + digits;
            }
            return said;
        });
        return out;
    }

    private static String scale(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "k", "thousand" -> "thousand";
            case "m", "million" -> "million";
            default -> "billion";
        };
    }

    /** 1999 "nineteen ninety-nine", 1900 "nineteen hundred", 2005 "two thousand five", 2024 "twenty twenty-four". */
    static String year(int year) {
        if (year >= 2000 && year < 2010) {
            return year == 2000 ? "two thousand" : "two thousand " + words(year % 100);
        }
        int high = year / 100;
        int low = year % 100;
        if (low == 0) {
            return words(high) + " hundred";
        }
        return words(high) + " " + (low < 10 ? "oh " + words(low) : words(low));
    }

    static String decade(int century, int tens) {
        String high = words(century);
        if (century == 20 && tens == 0) {
            return "two thousands";
        }
        String low = words(tens * 10L);
        String plural = low.endsWith("y") ? low.substring(0, low.length() - 1) + "ies" : low + "s";
        return high + " " + plural;
    }

    static String ordinal(long n) {
        String cardinal = words(n);
        String[][] irregular = {{"one", "first"}, {"two", "second"}, {"three", "third"}, {"five", "fifth"},
                {"eight", "eighth"}, {"nine", "ninth"}, {"twelve", "twelfth"}};
        for (String[] pair : irregular) {
            if (cardinal.endsWith(pair[0])) {
                return cardinal.substring(0, cardinal.length() - pair[0].length()) + pair[1];
            }
        }
        if (cardinal.endsWith("y")) {
            return cardinal.substring(0, cardinal.length() - 1) + "ieth";
        }
        return cardinal + "th";
    }

    /** Cardinal number in words, up to the billions: 6000000 "six million", 37 "thirty-seven". */
    public static String words(long n) {
        if (n < 0) {
            return "minus " + words(-n);
        }
        if (n < 20) {
            return ONES[(int) n];
        }
        if (n < 100) {
            return TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : "-" + ONES[(int) (n % 10)]);
        }
        if (n < 1000) {
            return ONES[(int) (n / 100)] + " hundred" + (n % 100 == 0 ? "" : " " + words(n % 100));
        }
        long[] scales = {1_000_000_000L, 1_000_000L, 1_000L};
        String[] names = {"billion", "million", "thousand"};
        for (int i = 0; i < scales.length; i++) {
            if (n >= scales[i]) {
                return words(n / scales[i]) + " " + names[i] + (n % scales[i] == 0 ? "" : " " + words(n % scales[i]));
            }
        }
        return String.valueOf(n);
    }

    private interface Said {
        String apply(Matcher m);
    }

    private static String replace(Pattern pattern, String text, Said said) {
        Matcher m = pattern.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(said.apply(m)));
        }
        m.appendTail(out);
        return out.toString();
    }
}
