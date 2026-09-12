package com.resolveiq.processors.watermark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Watermarking and Out-of-Order Telemetry Arrival Engine (PRD §13, §37).
 * Tracks maximum event timestamps per stream partition/tenant and tolerates
 * up to 60 seconds of out-of-order arrival before flagging data as late.
 */
@Component
public class WatermarkTracker {

    private static final Logger log = LoggerFactory.getLogger(WatermarkTracker.class);

    private final long allowedLatenessMs;
    private final Map<String, Instant> streamWatermarks = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> lateEventCounters = new ConcurrentHashMap<>();

    public WatermarkTracker(
            @Value("${resolveiq.processors.watermark.allowed-lateness-seconds:60}") long allowedLatenessSeconds) {
        this.allowedLatenessMs = allowedLatenessSeconds * 1000L;
    }

    /**
     * Assesses whether an event timestamp is late based on the stream's current watermark.
     *
     * @param streamKey identifier for the stream (e.g. "tenantId:serviceId" or partition id)
     * @param eventTime timestamp carried by the telemetry payload
     * @return true if event arrived past the allowed lateness window
     */
    public boolean isLate(String streamKey, Instant eventTime) {
        if (eventTime == null) {
            return false;
        }

        Instant watermark = streamWatermarks.get(streamKey);
        if (watermark == null) {
            return false;
        }

        long lagMs = Duration.between(eventTime, watermark).toMillis();
        boolean late = lagMs > allowedLatenessMs;

        if (late) {
            lateEventCounters.computeIfAbsent(streamKey, k -> new AtomicLong(0)).incrementAndGet();
            log.warn("Late telemetry detected on stream {}: eventTime={}, currentWatermark={}, lagMs={} (threshold={}ms)",
                    streamKey, eventTime, watermark, lagMs, allowedLatenessMs);
        }

        return late;
    }

    /**
     * Advances the watermark for the stream if the eventTime is newer than current watermark.
     *
     * @param streamKey identifier for the stream
     * @param eventTime timestamp carried by the telemetry payload
     */
    public void updateWatermark(String streamKey, Instant eventTime) {
        if (eventTime == null) {
            return;
        }

        streamWatermarks.compute(streamKey, (key, existing) -> {
            if (existing == null || eventTime.isAfter(existing)) {
                return eventTime;
            }
            return existing;
        });
    }

    /**
     * Returns the current watermark for the given stream, or null if uninitialized.
     */
    public Instant getWatermark(String streamKey) {
        return streamWatermarks.get(streamKey);
    }

    /**
     * Returns the count of late events observed on the stream.
     */
    public long getLateEventCount(String streamKey) {
        AtomicLong counter = lateEventCounters.get(streamKey);
        return counter != null ? counter.get() : 0L;
    }

    public long getAllowedLatenessMs() {
        return allowedLatenessMs;
    }

    public void clear() {
        streamWatermarks.clear();
        lateEventCounters.clear();
    }
}
