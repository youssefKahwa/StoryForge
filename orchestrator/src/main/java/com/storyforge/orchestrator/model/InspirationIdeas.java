package com.storyforge.orchestrator.model;

import com.storyforge.orchestrator.format.VideoFormat;
import java.util.List;

public record InspirationIdeas(
        String topic,
        String story,
        List<String> leads,
        List<String> whatWorked,
        List<Angle> angles,
        VideoFormat suggestedFormat,
        Integer suggestedMinutes,
        List<String> cautions) {

    public record Angle(String title, String angle, String whyOriginal) {
    }
}
