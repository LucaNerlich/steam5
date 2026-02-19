package org.steam5.web;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

public record ApiError(
    String timestamp,
    int status,
    String error,
    String message,
    String correlationId,
    String path
) {
    public static ApiError of(int status, String message, String path) {
        String corrId = MDC.get("correlationId");
        return new ApiError(
            OffsetDateTime.now().toString(),
            status,
            HttpStatus.valueOf(status).getReasonPhrase(),
            message,
            corrId,
            path
        );
    }
}