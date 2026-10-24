package com.storyforge.orchestrator.script;

import com.storyforge.orchestrator.model.InspirationIdeas;
import com.storyforge.orchestrator.settings.PreferencesService;
import com.storyforge.orchestrator.stage.Stage;
import com.storyforge.orchestrator.stage.StageContext;
import com.storyforge.orchestrator.stage.worker.DiscoverStage;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who is talking in the script: the channel brief, the voice of the owner's inspiration videos and the story they
 * tell. Shared by the script and rewrite prompts so a rewrite keeps the same voice.
 */
@Component
public class NarrationStyle {

    static final String DEFAULT_VOICE = "{\"persona\": \"a passionate storyteller who knows this story inside out and "
            + "can't wait to tell you\", \"energy\": \"warm, lively, builds tension then lets it breathe\", "
            + "\"humor\": \"light, when something is absurd\", \"viewer\": \"talks to you directly, asks you questions\"}";

    private final PreferencesService preferences;
    private final JsonMapper json;

    public NarrationStyle(PreferencesService preferences, JsonMapper json) {
        this.preferences = preferences;
        this.json = json;
    }

    public Map<String, String> vars(StageContext context) {
        InspirationIdeas ideas = context.files().exists(Stage.DISCOVER, DiscoverStage.INSPIRATION)
                ? context.files().read(Stage.DISCOVER, DiscoverStage.INSPIRATION, InspirationIdeas.class) : null;
        String brief = preferences.get().channelBrief();
        return Map.of(
                "channel_brief", brief == null || brief.isBlank() ? PreferencesService.DEFAULT_BRIEF : brief,
                "voice", ideas != null && ideas.voice() != null ? json.writeValueAsString(ideas.voice()) : DEFAULT_VOICE,
                "story", ideas != null && ideas.story() != null ? ideas.story() : "(no summary: rely on the claims)");
    }
}
