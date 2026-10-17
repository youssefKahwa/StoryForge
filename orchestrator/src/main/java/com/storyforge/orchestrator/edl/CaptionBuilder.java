package com.storyforge.orchestrator.edl;

import com.storyforge.orchestrator.model.Alignment.Word;
import com.storyforge.orchestrator.model.Edl.EdlCaption;
import java.util.ArrayList;
import java.util.List;

public final class CaptionBuilder {

    private CaptionBuilder() {
    }

    public static List<EdlCaption> build(List<Word> words, int maxChars, double maxSeconds) {
        List<EdlCaption> captions = new ArrayList<>();
        List<Word> line = new ArrayList<>();
        for (Word word : words) {
            if (!line.isEmpty()) {
                Word first = line.get(0);
                int length = text(line).length() + 1 + word.text().length();
                boolean sceneChanged = !first.sceneId().equals(word.sceneId());
                if (length > maxChars || word.end() - first.start() > maxSeconds || sceneChanged) {
                    captions.add(caption(line));
                    line.clear();
                }
            }
            line.add(word);
            if (word.endsSentence()) {
                captions.add(caption(line));
                line.clear();
            }
        }
        if (!line.isEmpty()) {
            captions.add(caption(line));
        }
        return captions;
    }

    public static String toSrt(List<EdlCaption> captions) {
        StringBuilder srt = new StringBuilder();
        for (int i = 0; i < captions.size(); i++) {
            EdlCaption caption = captions.get(i);
            srt.append(i + 1).append('\n')
                    .append(timestamp(caption.start())).append(" --> ").append(timestamp(caption.end())).append('\n')
                    .append(caption.text()).append("\n\n");
        }
        return srt.toString();
    }

    static String timestamp(double seconds) {
        long millis = Math.round(seconds * 1000);
        return "%02d:%02d:%02d,%03d".formatted(millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60,
                millis % 1000);
    }

    private static EdlCaption caption(List<Word> line) {
        return new EdlCaption(line.get(0).start(), line.get(line.size() - 1).end(), text(line));
    }

    private static String text(List<Word> line) {
        return String.join(" ", line.stream().map(Word::text).toList());
    }
}
