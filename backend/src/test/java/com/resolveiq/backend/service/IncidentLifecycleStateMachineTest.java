package com.resolveiq.backend.service;

import com.resolveiq.backend.exception.InvalidLifecycleTransitionException;
import com.resolveiq.common.incident.IncidentEventType;
import com.resolveiq.common.incident.IncidentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentLifecycleStateMachineTest {

    private IncidentLifecycleStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new IncidentLifecycleStateMachine();
    }

    @Test
    @DisplayName("Valid transition path: DETECTED -> INVESTIGATING -> IDENTIFIED -> MITIGATING -> MONITORING -> RESOLVED -> CLOSED")
    void testCanonicalLifecyclePath() {
        assertThatCode(() -> {
            stateMachine.validateTransition(IncidentStatus.DETECTED, IncidentStatus.INVESTIGATING);
            stateMachine.validateTransition(IncidentStatus.INVESTIGATING, IncidentStatus.IDENTIFIED);
            stateMachine.validateTransition(IncidentStatus.IDENTIFIED, IncidentStatus.MITIGATING);
            stateMachine.validateTransition(IncidentStatus.MITIGATING, IncidentStatus.MONITORING);
            stateMachine.validateTransition(IncidentStatus.MONITORING, IncidentStatus.RESOLVED);
            stateMachine.validateTransition(IncidentStatus.RESOLVED, IncidentStatus.CLOSED);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Valid skip transitions allowed by PRD §19.1 (e.g. fast-forward mitigation or direct manual resolution)")
    void testAllowedSkipTransitions() {
        // INVESTIGATING can transition directly to MITIGATING (skipping IDENTIFIED)
        assertThatCode(() -> stateMachine.validateTransition(IncidentStatus.INVESTIGATING, IncidentStatus.MITIGATING))
                .doesNotThrowAnyException();

        // INVESTIGATING can transition directly to RESOLVED (manual resolution of false alarms)
        assertThatCode(() -> stateMachine.validateTransition(IncidentStatus.INVESTIGATING, IncidentStatus.RESOLVED))
                .doesNotThrowAnyException();

        // IDENTIFIED can transition directly to RESOLVED
        assertThatCode(() -> stateMachine.validateTransition(IncidentStatus.IDENTIFIED, IncidentStatus.RESOLVED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Guarded transitions: DETECTED cannot skip INVESTIGATING to go directly to RESOLVED (PRD §19.1)")
    void testDetectedCannotSkipToResolved() {
        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.DETECTED, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidLifecycleTransitionException.class)
                .hasMessageContaining("PRD §19.1 prohibits skipping mandatory lifecycle phases");
    }

    @Test
    @DisplayName("Guarded transitions: DETECTED cannot go directly to CLOSED or MITIGATING")
    void testDetectedCannotGoDirectlyToClosedOrMitigating() {
        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.DETECTED, IncidentStatus.CLOSED))
                .isInstanceOf(InvalidLifecycleTransitionException.class);

        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.DETECTED, IncidentStatus.MITIGATING))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }

    @Test
    @DisplayName("Reopening transitions: RESOLVED and CLOSED can reopen to INVESTIGATING on regression")
    void testReopeningTransitions() {
        assertThatCode(() -> stateMachine.validateTransition(IncidentStatus.RESOLVED, IncidentStatus.INVESTIGATING))
                .doesNotThrowAnyException();

        assertThatCode(() -> stateMachine.validateTransition(IncidentStatus.CLOSED, IncidentStatus.INVESTIGATING))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Terminal safety: CLOSED cannot jump to RESOLVED or MITIGATING without reopening")
    void testClosedCannotJumpWithoutReopening() {
        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.CLOSED, IncidentStatus.RESOLVED))
                .isInstanceOf(InvalidLifecycleTransitionException.class);

        assertThatThrownBy(() -> stateMachine.validateTransition(IncidentStatus.CLOSED, IncidentStatus.MITIGATING))
                .isInstanceOf(InvalidLifecycleTransitionException.class);
    }

    @ParameterizedTest
    @EnumSource(IncidentStatus.class)
    @DisplayName("Same-state transitions are safe no-ops")
    void testSameStateNoOp(IncidentStatus status) {
        assertThatCode(() -> stateMachine.validateTransition(status, status)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Timeline event mapping for lifecycle transitions")
    void testTimelineEventMapping() {
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.INVESTIGATING))
                .isEqualTo(IncidentEventType.INVESTIGATION_STARTED);
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.IDENTIFIED))
                .isEqualTo(IncidentEventType.HYPOTHESIS_GENERATED);
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.MITIGATING))
                .isEqualTo(IncidentEventType.MITIGATION_APPLIED);
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.MONITORING))
                .isEqualTo(IncidentEventType.MONITORING_STARTED);
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.RESOLVED))
                .isEqualTo(IncidentEventType.RESOLVED);
        assertThat(stateMachine.getTimelineEventTypeForTransition(IncidentStatus.CLOSED))
                .isEqualTo(IncidentEventType.CLOSED);
    }
}
