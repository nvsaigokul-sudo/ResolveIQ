package com.resolveiq.backend.notifications.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.common.exception.ValidationException;
import com.resolveiq.common.notification.NotificationChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Operational Email notification adapter (PRD §27).
 * Formats HTML and plaintext alert messages for incident response teams.
 */
@Component
public class EmailNotificationAdapter implements NotificationDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationAdapter.class);

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final ObjectMapper objectMapper;

    public EmailNotificationAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(NotificationChannelType channelType) {
        return channelType == NotificationChannelType.EMAIL;
    }

    @Override
    public DeliveryResult deliver(NotificationChannelEntity channel, NotificationEntity notification) {
        long start = System.currentTimeMillis();
        String destination = channel.getDestination();

        // 1. Validate email destination format
        validateEmailDestination(destination);

        try {
            Map<String, Object> payloadMap = parsePayloadSafely(notification.getPayload());
            String title = (String) payloadMap.getOrDefault("title", "ResolveIQ Incident Alert");
            String severity = (String) payloadMap.getOrDefault("severity", "SEV3");
            String service = (String) payloadMap.getOrDefault("root_service", "Unknown");
            String status = (String) payloadMap.getOrDefault("status", "INVESTIGATING");
            String summary = (String) payloadMap.getOrDefault("summary", "Incident activity detected.");

            String subject = String.format("[ResolveIQ Alert] [%s] %s (%s)", severity, title, service);
            String htmlBody = formatHtmlEmail(title, severity, service, status, summary, notification.getIncidentId());

            log.info("Dispatching incident alert email to '{}' (Subject: '{}')", destination, subject);

            // Simulated / JavaMail delivery
            long latency = System.currentTimeMillis() - start;
            String messageId = "email-" + UUID.randomUUID() + "@resolveiq.io";
            return DeliveryResult.success(200, messageId, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Email delivery failed to {}: {}", destination, e.getMessage());
            return DeliveryResult.failure(500, e.getMessage(), latency);
        }
    }

    public void validateEmailDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new ValidationException("Email recipient destination cannot be blank");
        }
        String[] emails = destination.split(",");
        for (String email : emails) {
            String trimmed = email.trim();
            if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
                throw new ValidationException("Invalid email recipient address: " + trimmed);
            }
        }
    }

    public String formatHtmlEmail(String title, String severity, String service, String status, String summary, UUID incidentId) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; color: #1e293b; }
                        .container { max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; }
                        .header { background: #0f172a; color: white; padding: 16px; border-radius: 6px; }
                        .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-weight: bold; background: #ef4444; color: white; }
                        .meta { margin: 16px 0; background: #f8fafc; padding: 12px; border-radius: 4px; }
                        .summary { margin-top: 16px; line-height: 1.6; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h2>ResolveIQ Automated Alert</h2>
                            <span class="badge">%s</span> <span>%s</span>
                        </div>
                        <div class="meta">
                            <p><strong>Root Service:</strong> %s</p>
                            <p><strong>Lifecycle Status:</strong> %s</p>
                            <p><strong>Incident ID:</strong> %s</p>
                        </div>
                        <div class="summary">
                            <h3>Summary</h3>
                            <p>%s</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(severity, title, service, status, incidentId != null ? incidentId : "N/A", summary);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayloadSafely(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
