package com.storyforge.orchestrator.dto;

import com.storyforge.orchestrator.revision.Revision;
import com.storyforge.orchestrator.revision.RevisionKind;
import com.storyforge.orchestrator.revision.RevisionStatus;
import java.time.Instant;

public record RevisionDto(long id, RevisionKind kind, String target, Double atSec, String note, RevisionStatus status,
                          String result, Instant createdAt, Instant updatedAt) {

    public static RevisionDto from(Revision r) {
        return new RevisionDto(r.getId(), r.getKind(), r.getTarget(), r.getAtSec(), r.getNote(), r.getStatus(),
                r.getResult(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
