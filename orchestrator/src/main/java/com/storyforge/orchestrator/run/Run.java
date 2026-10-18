package com.storyforge.orchestrator.run;

import com.storyforge.orchestrator.format.VideoFormat;
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

@Entity
@Table(name = "run")
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seed_topic_id")
    private Long seedTopicId;

    @Column(name = "topic_title", nullable = false)
    private String topicTitle;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private VideoFormat format;

    @Column(nullable = false)
    private String language;

    @Column(nullable = false)
    private String profile;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private RunState state;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private RunStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "target_seconds", nullable = false)
    private int targetSeconds;

    @Column(name = "cache_salt", nullable = false)
    private int cacheSalt;

    @Column(name = "inspiration_id")
    private Long inspirationId;

    private String angle;

    private String voice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public Long getId() {
        return id;
    }

    public Long getSeedTopicId() {
        return seedTopicId;
    }

    public void setSeedTopicId(Long seedTopicId) {
        this.seedTopicId = seedTopicId;
    }

    public String getTopicTitle() {
        return topicTitle;
    }

    public void setTopicTitle(String topicTitle) {
        this.topicTitle = topicTitle;
    }

    public VideoFormat getFormat() {
        return format;
    }

    public void setFormat(VideoFormat format) {
        this.format = format;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public RunState getState() {
        return state;
    }

    public void setState(RunState state) {
        this.state = state;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public int getTargetSeconds() {
        return targetSeconds;
    }

    public void setTargetSeconds(int targetSeconds) {
        this.targetSeconds = targetSeconds;
    }

    public int getCacheSalt() {
        return cacheSalt;
    }

    public void setCacheSalt(int cacheSalt) {
        this.cacheSalt = cacheSalt;
    }

    public Long getInspirationId() {
        return inspirationId;
    }

    public void setInspirationId(Long inspirationId) {
        this.inspirationId = inspirationId;
    }

    public String getAngle() {
        return angle;
    }

    public void setAngle(String angle) {
        this.angle = angle;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}
