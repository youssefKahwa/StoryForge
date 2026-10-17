package com.storyforge.orchestrator.dto;

import java.time.Instant;
import org.springframework.http.HttpStatus;

public record ErrorDto(int status, String error, String message, Instant timestamp) {

    public static ErrorDto from(HttpStatus status, String message) {
        return new ErrorDto(status.value(), status.getReasonPhrase(), message, Instant.now());
    }
}
