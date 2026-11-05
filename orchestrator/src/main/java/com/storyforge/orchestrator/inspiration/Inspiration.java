package com.storyforge.orchestrator.inspiration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inspiration")
public class Inspiration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private InspirationStatus status;

    @Column(nullable = false)
    private String urls;

    @Column(name = "pasted_text")
    private String pastedText;

    private String error;

    @Column(name = "auto_start", nullable = false)
    private boolean autoStart;

    @Column(name = "owner_notes")
    private String ownerNotes;

    /** The owner's main video (also the first of the urls): the script follows it part by part. */
    @Column(name = "main_url")
    private String mainUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public InspirationStatus getStatus() {
        return status;
    }

    public void setStatus(InspirationStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public List<String> getUrlList() {
        return urls == null || urls.isBlank() ? List.of() : Arrays.asList(urls.split("\n"));
    }

    public void setUrlList(List<String> list) {
        this.urls = String.join("\n", list);
    }

    public String getPastedText() {
        return pastedText;
    }

    public void setPastedText(String pastedText) {
        this.pastedText = pastedText;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public String getOwnerNotes() {
        return ownerNotes;
    }

    public String getMainUrl() {
        return mainUrl;
    }

    public void setMainUrl(String mainUrl) {
        this.mainUrl = mainUrl;
    }

    public void setOwnerNotes(String ownerNotes) {
        this.ownerNotes = ownerNotes;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
