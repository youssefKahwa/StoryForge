package com.storyforge.orchestrator.model;

import java.util.List;

public record VideoPackage(
        String title,
        List<ScoredTitle> titles,
        String description,
        List<String> tags,
        String pinnedComment,
        String thumbnail,
        String thumbnailText,
        List<String> thumbnailAlternates,
        List<Edl.EdlChapter> chapters,
        boolean disclosureRequired) {

    public record ScoredTitle(String title, double score, List<String> reasons) {
    }
}
