package com.resolveiq.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Collections;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(ErrorBody error) {

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(new ErrorBody(code, message, traceId, Collections.emptyMap()));
    }

    public static ErrorResponse of(String code, String message, String traceId, Map<String, Object> details) {
        return new ErrorResponse(new ErrorBody(code, message, traceId, details != null ? details : Collections.emptyMap()));
    }

    public record ErrorBody(
            String code,
            String message,
            String traceId,
            Map<String, Object> details
    ) {}
}
