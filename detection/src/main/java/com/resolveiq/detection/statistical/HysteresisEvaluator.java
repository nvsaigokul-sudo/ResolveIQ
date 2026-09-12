package com.resolveiq.detection.statistical;

import org.springframework.stereotype.Component;

/**
 * Dual-Threshold Hysteresis Evaluator (PRD §16, §17).
 * Prevents alert flapping around noisy threshold boundaries by requiring:
 * 1. Anomaly trigger: observed value crosses T_trigger.
 * 2. Anomaly recovery: observed value drops below T_recovery (< T_trigger) for consecutive evaluation cycles.
 */
@Component
public class HysteresisEvaluator {

    /**
     * Evaluates state transition based on dual-threshold hysteresis.
     *
     * @param isCurrentlyActive     true if the anomaly is currently in an active (RAISED/EVALUATED) state
     * @param observedValue         current telemetry value
     * @param triggerThreshold      threshold required to initiate an anomaly
     * @param recoveryThreshold     lower threshold required to permit recovery
     * @param consecutiveNormalCount count of consecutive times value has been below recovery threshold
     * @param requiredNormalCycles  number of consecutive normal readings required to confirm resolution (e.g. 3)
     * @return true if anomaly should be considered active; false if it should be resolved
     */
    public boolean evaluateActiveState(
            boolean isCurrentlyActive,
            double observedValue,
            double triggerThreshold,
            double recoveryThreshold,
            int consecutiveNormalCount,
            int requiredNormalCycles) {

        if (isCurrentlyActive) {
            // Already active: only resolve if value dropped below recovery threshold AND held for required cycles
            if (observedValue <= recoveryThreshold && consecutiveNormalCount >= requiredNormalCycles) {
                return false; // Resolved
            }
            return true; // Still active (in dead-band or above trigger)
        } else {
            // Currently inactive: trigger breach if value crosses trigger threshold
            return observedValue >= triggerThreshold;
        }
    }
}
