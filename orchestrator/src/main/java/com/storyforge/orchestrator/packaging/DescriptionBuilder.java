package com.storyforge.orchestrator.packaging;

import com.storyforge.orchestrator.model.Dossier;
import com.storyforge.orchestrator.model.Edl.EdlChapter;
import java.util.List;

public final class DescriptionBuilder {

    public static final String AI_DISCLOSURE =
            "Narration and illustrations in this video were produced with AI tools; research and sources are listed above.";

    private DescriptionBuilder() {
    }

    public static String build(String hookLine, List<EdlChapter> chapters, List<Dossier.Source> sources) {
        StringBuilder out = new StringBuilder();
        out.append(hookLine.strip()).append("\n\n");
        if (!chapters.isEmpty()) {
            out.append("Chapters\n");
            chapters.forEach(chapter -> out.append(timestamp(chapter.start())).append(' ')
                    .append(chapter.title()).append('\n'));
            out.append('\n');
        }
        if (!sources.isEmpty()) {
            out.append("Sources\n");
            sources.forEach(source -> out.append("- ").append(source.title())
                    .append(source.publisher() == null ? "" : " (" + source.publisher() + ")")
                    .append(": ").append(source.url()).append('\n'));
            out.append('\n');
        }
        out.append(AI_DISCLOSURE).append('\n');
        return out.toString();
    }

    public static String timestamp(double seconds) {
        long total = (long) Math.floor(seconds);
        long hours = total / 3600;
        long minutes = total / 60 % 60;
        long secs = total % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, secs) : "%d:%02d".formatted(minutes, secs);
    }
}
