package com.resolveiq.detection.statistical;

import com.resolveiq.common.telemetry.DetectorType;
import com.resolveiq.common.telemetry.Severity;
import com.resolveiq.detection.Detector;
import com.resolveiq.detection.model.DetectionEvaluationResult;
import com.resolveiq.detection.model.MetricSample;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seasonality-Aware Baseline Detector (DoD / WoW) (PRD §17).
 * Compares current observed telemetry against expected historical seasonal baselines
 * (same time of day, weekday vs weekend) and detects abnormal percentage deviations.
 */
@Component
public class SeasonalityBaselineDetector implements Detector {

    public static final String DETECTOR_ID = "stat.seasonality_baseline";

    @Override
    public String getId() {
        return DETECTOR_ID;
    }

    @Override
    public DetectorType getType() {
        return DetectorType.STATISTICAL;
    }

    @Override
    public DetectionEvaluationResult evaluate(List<MetricSample> samples, Map<String, Object> parameters) {
        if (samples == null || samples.isEmpty()) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), 0, 1);
        }

        // Expected baseline value from DoD (Day-over-Day) or WoW (Week-over-Week) reference
        Double seasonalBaseline = getDoubleParamOrNull(parameters, "seasonal_baseline");
        if (seasonalBaseline == null) {
            return DetectionEvaluationResult.insufficientData(getId(), getType(), samples.size(), 1);
        }

        double maxAllowedDeviationPercent = getDoubleParam(parameters, "max_deviation_percent", 50.0); // e.g. 50%
        double criticalDeviationPercent = getDoubleParam(parameters, "critical_deviation_percent", 100.0);

        // Compute average observed across current window
        double sum = 0.0;
        for (MetricSample s : samples) {
            sum += s.value();
        }
        double observedAvg = sum / samples.size();

        double denominator = Math.max(Math.abs(seasonalBaseline), 1.0);
        double deviationPercent = ((observedAvg - seasonalBaseline) / denominator) * 100.0;

        if (Math.abs(deviationPercent) >= maxAllowedDeviationPercent) {
            Severity severity = Math.abs(deviationPercent) >= criticalDeviationPercent ? Severity.CRITICAL : Severity.HIGH;
            String direction = deviationPercent > 0 ? "spike above" : "drop below";
            String msg = String.format("Seasonal baseline deviation: observed=%.2f is %.1f%% %s expected baseline %.2f (allowed=%.1f%%)",
                    observedAvg, Math.abs(deviationPercent), direction, seasonalBaseline, maxAllowedDeviationPercent);

            return DetectionEvaluationResult.breach(
                    getId(), getType(), observedAvg, seasonalBaseline, severity, samples.size(),
                    null, msg, Map.of(
                            "seasonal_baseline", seasonalBaseline,
                            "observed_avg", observedAvg,
                            "deviation_percent", deviationPercent,
                            "max_allowed_percent", maxAllowedDeviationPercent
                    )
            );
        }

        return DetectionEvaluationResult.ok(getId(), getType(), observedAvg, seasonalBaseline, samples.size());
    }

    private Double getDoubleParamOrNull(Map<String, Object> params, String key) {
        if (params != null && params.containsKey(key)) {
            Object v = params.get(key);
            if (v instanceof Number n) return n.doubleValue();
        }
        return null;
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultVal) {
        if (params != null && params.containsKey(key)) {
            Object v = params.get(key);
            if (v instanceof Number n) return n.doubleValue();
        }
        return defaultVal;
    }
}
