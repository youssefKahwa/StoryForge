package com.storyforge.orchestrator.revision;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One owner note on a finished video: "redo this image", "rewrite this part", "less music here"... */
@Entity
@Table(name = "revision")
public class Revision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private RevisionKind kind;

    @Column(nullable = false)
    private String target;

    @Column(name = "at_sec")
    private Double atSec;

    @Column(nullable = false)
    private String note;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private RevisionStatus status;

    private String result;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Revision() {
    }

    public Revision(Long runId, RevisionKind kind, String target, Double atSec, String note) {
        this.runId = runId;
        this.kind = kind;
        this.target = target;
        this.atSec = atSec;
        this.note = note;
        this.status = RevisionStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getRunId() {
        return runId;
    }

    public RevisionKind getKind() {
        return kind;
    }

    public String getTarget() {
        return target;
    }

    public Double getAtSec() {
        return atSec;
    }

    public String getNote() {
        return note;
    }

    public RevisionStatus getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void mark(RevisionStatus status, String result) {
        this.status = status;
        this.result = result;
        this.updatedAt = Instant.now();
    }
}
