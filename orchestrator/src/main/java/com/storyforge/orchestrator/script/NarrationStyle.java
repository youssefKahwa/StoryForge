package com.storyforge.orchestrator.script;

import com.storyforge.orchestrator.inspiration.InspirationFiles;
import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.model.VideoTranscript;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.worker.DiscoverStage;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who is talking in the script: the channel brief, the voice of the owner's inspiration videos, the story they
 * tell and their own words to adapt from. Shared by the script and rewrite prompts so a rewrite keeps the same voice.
 */
@Component
public class NarrationStyle {

    static final String DEFAULT_VOICE = "{\"persona\": \"a passionate storyteller who knows this story inside out and "
            + "can't wait to tell you\", \"energy\": \"warm, lively, builds tension then lets it breathe\", "
            + "\"humor\": \"light, when something is absurd\", \"viewer\": \"talks to you directly, asks you questions\"}";
    /** Enough of each inspiration video for the writer to follow it closely without flooding the prompt. */
    private static final int SOURCE_CHARS_PER_VIDEO = 9000;
    private static final int SOURCE_CHARS_TOTAL = 18000;
    /** The owner's main video gets room for its whole script (a 10-20 minute video). */
    private static final int MAIN_CHARS = 24000;

    private final PreferencesService preferences;
    private final JsonMapper json;
    private final InspirationFiles inspirations;
    private final com.storyforge.orchestrator.inspiration.InspirationRepository inspirationRows;

    public NarrationStyle(PreferencesService preferences, JsonMapper json, InspirationFiles inspirations,
                          com.storyforge.orchestrator.inspiration.InspirationRepository inspirationRows) {
        this.inspirationRows = inspirationRows;
        this.preferences = preferences;
        this.json = json;
        this.inspirations = inspirations;
    }

    public Map<String, String> vars(StageContext context) {
        InspirationIdeas ideas = context.files().exists(Stage.DISCOVER, DiscoverStage.INSPIRATION)
                ? context.files().read(Stage.DISCOVER, DiscoverStage.INSPIRATION, InspirationIdeas.class) : null;
        String brief = preferences.get().channelBrief();
        return Map.of(
                "channel_brief", brief == null || brief.isBlank() ? PreferencesService.DEFAULT_BRIEF : brief,
                "voice", ideas != null && ideas.voice() != null ? json.writeValueAsString(ideas.voice()) : DEFAULT_VOICE,
                "story", ideas != null && ideas.story() != null ? ideas.story() : "(no summary: rely on the claims)",
                "source", source(context));
    }

    /** The inspiration videos' own transcripts (any language), the material the script adapts. */
    private String source(StageContext context) {
        Long id = context.run().inspirationId();
        if (id == null) {
            return "(none)";
        }
        List<VideoTranscript> transcripts = inspirations.transcripts(id);
        String mainUrl = inspirationRows.findById(id).map(i -> i.getMainUrl()).orElse(null);
        StringBuilder out = new StringBuilder();
        for (VideoTranscript t : transcripts) {
            boolean main = mainUrl != null && mainUrl.equals(t.url());
            int limit = main ? MAIN_CHARS : SOURCE_CHARS_TOTAL;
            if (t.transcript() == null || t.transcript().isBlank() || out.length() >= limit) {
                continue;
            }
            String text = t.transcript().strip();
            int room = main ? MAIN_CHARS : Math.min(SOURCE_CHARS_PER_VIDEO, SOURCE_CHARS_TOTAL - out.length());
            out.append(main ? "### MAIN VIDEO (follow it part by part): " : "### ")
                    .append(t.title() == null ? "Video" : t.title()).append('\n')
                    .append(text.length() > room ? text.substring(0, room) + " [...]" : text).append("\n\n");
        }
        return out.length() == 0 ? "(none)" : out.toString();
    }
}
