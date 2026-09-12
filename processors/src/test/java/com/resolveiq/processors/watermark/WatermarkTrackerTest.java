package com.resolveiq.processors.watermark;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class WatermarkTrackerTest {

    private WatermarkTracker watermarkTracker;

    @BeforeEach
    void setUp() {
        // 60-second allowed lateness per PRD §13, §37
        watermarkTracker = new WatermarkTracker(60);
    }

    @Test
    @DisplayName("On-time telemetry updates stream watermark")
    void testOnTimeTelemetryAdvancesWatermark() {
        String streamKey = "tenant-1:payment-service";
        Instant t0 = Instant.parse("2026-09-12T12:00:00Z");
        Instant t1 = Instant.parse("2026-09-12T12:01:00Z");

        watermarkTracker.updateWatermark(streamKey, t0);
        assertThat(watermarkTracker.getWatermark(streamKey)).isEqualTo(t0);

        assertThat(watermarkTracker.isLate(streamKey, t1)).isFalse();
        watermarkTracker.updateWatermark(streamKey, t1);
        assertThat(watermarkTracker.getWatermark(streamKey)).isEqualTo(t1);
    }

    @Test
    @DisplayName("Out-of-order telemetry within 60s tolerance is accepted as on-time")
    void testOutOfOrderWithinToleranceAccepted() {
        String streamKey = "tenant-1:order-service";
        Instant t0 = Instant.parse("2026-09-12T12:01:00Z");
        watermarkTracker.updateWatermark(streamKey, t0);

        // 30 seconds before current watermark (within 60s threshold)
        Instant tLag30s = Instant.parse("2026-09-12T12:00:30Z");
        assertThat(watermarkTracker.isLate(streamKey, tLag30s)).isFalse();
        assertThat(watermarkTracker.getLateEventCount(streamKey)).isEqualTo(0);
    }

    @Test
    @DisplayName("Telemetry arriving >60s late is flagged and tracked")
    void testTelemetryPast60sFlaggedLate() {
        String streamKey = "tenant-1:auth-service";
        Instant t0 = Instant.parse("2026-09-12T12:05:00Z");
        watermarkTracker.updateWatermark(streamKey, t0);

        // 75 seconds before current watermark (> 60s threshold)
        Instant tLate75s = Instant.parse("2026-09-12T12:03:45Z");
        assertThat(watermarkTracker.isLate(streamKey, tLate75s)).isTrue();
        assertThat(watermarkTracker.getLateEventCount(streamKey)).isEqualTo(1);

        // Another late event
        Instant tLate120s = Instant.parse("2026-09-12T12:03:00Z");
        assertThat(watermarkTracker.isLate(streamKey, tLate120s)).isTrue();
        assertThat(watermarkTracker.getLateEventCount(streamKey)).isEqualTo(2);
    }

    @Test
    @DisplayName("Independent streams maintain isolated watermarks")
    void testMultipleStreamWatermarkIsolation() {
        String streamA = "tenant-alpha:service-1";
        String streamB = "tenant-beta:service-1";

        Instant tA = Instant.parse("2026-09-12T12:10:00Z");
        Instant tB = Instant.parse("2026-09-12T12:00:00Z");

        watermarkTracker.updateWatermark(streamA, tA);
        watermarkTracker.updateWatermark(streamB, tB);

        assertThat(watermarkTracker.getWatermark(streamA)).isEqualTo(tA);
        assertThat(watermarkTracker.getWatermark(streamB)).isEqualTo(tB);

        // An event at 12:00:30 is on-time for Stream B, but late for Stream A
        Instant testTime = Instant.parse("2026-09-12T12:00:30Z");
        assertThat(watermarkTracker.isLate(streamB, testTime)).isFalse();
        assertThat(watermarkTracker.isLate(streamA, testTime)).isTrue();
    }
}
