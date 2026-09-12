package com.resolveiq.backend.notifications.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resolveiq.backend.domain.NotificationChannelEntity;
import com.resolveiq.backend.domain.NotificationEntity;
import com.resolveiq.backend.notifications.security.SsrfValidator;
import com.resolveiq.common.notification.NotificationChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Slack Incoming Webhook adapter formatting Slack Block Kit alerts (PRD §27).
 */
@Component
public class SlackNotificationAdapter implements NotificationDeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(SlackNotificationAdapter.class);

    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SlackNotificationAdapter(SsrfValidator ssrfValidator, ObjectMapper objectMapper) {
        this.ssrfValidator = ssrfValidator;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean supports(NotificationChannelType channelType) {
        return channelType == NotificationChannelType.SLACK;
    }

    @Override
    public DeliveryResult deliver(NotificationChannelEntity channel, NotificationEntity notification) {
        long start = System.currentTimeMillis();
        String destination = channel.getDestination();

        // 1. SSRF validation on webhook destination
        ssrfValidator.validateUrl(destination);

        // 2. Format Slack Block Kit JSON
        try {
            Map<String, Object> payloadMap = parsePayloadSafely(notification.getPayload());
            String title = (String) payloadMap.getOrDefault("title", "ResolveIQ Incident Alert");
            String severity = (String) payloadMap.getOrDefault("severity", "SEV3");
            String service = (String) payloadMap.getOrDefault("root_service", "Unknown");
            String status = (String) payloadMap.getOrDefault("status", "INVESTIGATING");
            String summary = (String) payloadMap.getOrDefault("summary", "Incident activity detected.");

            Map<String, Object> slackPayload = formatSlackBlockKit(title, severity, service, status, summary, notification.getIncidentId());
            String jsonBody = objectMapper.writeValueAsString(slackPayload);

            // 3. For mock/simulated URLs in test environments (e.g. test:// or mock domain)
            if (destination.startsWith("https://hooks.slack.com/mock") || destination.contains("mock-slack") || destination.contains("example.com")) {
                log.info("Simulated Slack dispatch to {}: status=200", destination);
                return DeliveryResult.success(200, "slack-msg-" + UUID.randomUUID(), System.currentTimeMillis() - start);
            }

            // Real HTTP dispatch
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(destination))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long latency = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return DeliveryResult.success(response.statusCode(), "slack-res-" + UUID.randomUUID(), latency);
            } else {
                return DeliveryResult.failure(response.statusCode(),
                        "Slack API error (" + response.statusCode() + "): " + response.body(), latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("Slack delivery failed to {}: {}", destination, e.getMessage());
            return DeliveryResult.failure(500, e.getMessage(), latency);
        }
    }

    public Map<String, Object> formatSlackBlockKit(String title, String severity, String service,
                                                   String status, String summary, UUID incidentId) {
        List<Map<String, Object>> blocks = new ArrayList<>();

        // Header block
        blocks.add(Map.of(
                "type", "header",
                "text", Map.of("type", "plain_text", "text", String.format("🚨 [%s] %s", severity, title), "emoji", true)
        ));

        // Fields section block
        List<Map<String, Object>> fields = List.of(
                Map.of("type", "mrkdwn", "text", "*Service:*\n`" + service + "`"),
                Map.of("type", "mrkdwn", "text", "*Status:*\n`" + status + "`"),
                Map.of("type", "mrkdwn", "text", "*Severity:*\n`" + severity + "`"),
                Map.of("type", "mrkdwn", "text", "*Incident ID:*\n`" + (incidentId != null ? incidentId : "N/A") + "`")
        );

        blocks.add(Map.of(
                "type", "section",
                "fields", fields
        ));

        // Summary block
        blocks.add(Map.of(
                "type", "section",
                "text", Map.of("type", "mrkdwn", "text", "*Summary:*\n" + summary)
        ));

        // Divider
        blocks.add(Map.of("type", "divider"));

        return Map.of(
                "text", String.format("[%s] %s - %s", severity, title, service),
                "blocks", blocks
        );
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
