package com.resolveiq.backend.notifications.adapters;

public record DeliveryResult(
        boolean success,
        int statusCode,
        String externalMessageId,
        String errorMessage,
        long latencyMs
) {
    public static DeliveryResult success(int statusCode, String externalMessageId, long latencyMs) {
        return new DeliveryResult(true, statusCode, externalMessageId, null, latencyMs);
    }

    public static DeliveryResult failure(int statusCode, String errorMessage, long latencyMs) {
        return new DeliveryResult(false, statusCode, null, errorMessage, latencyMs);
    }
}
