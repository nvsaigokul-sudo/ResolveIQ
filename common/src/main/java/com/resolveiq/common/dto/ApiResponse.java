package com.resolveiq.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        List<T> items,
        T data,
        String nextCursor,
        Long total
) {
    public static <T> ApiResponse<T> ofItems(List<T> items, String nextCursor, Long total) {
        return new ApiResponse<>(items, null, nextCursor, total);
    }

    public static <T> ApiResponse<T> ofData(T data) {
        return new ApiResponse<>(null, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ofData(data);
    }
}
