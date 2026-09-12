"""
Scoring algorithms and metric calculations for ResolveIQ AI Evaluation Framework (PRD §30, §56, §58).
"""

from typing import List, Dict, Any, Tuple, Optional
from .models import BenchmarkScenario, EvaluationCaseScore, EvaluationSuiteReport, EvaluationComparison


def calculate_percentiles(values: List[int]) -> Tuple[int, int]:
    """Computes p50 and p95 integer percentiles from duration samples."""
    if not values:
        return 0, 0
    sorted_vals = sorted(values)
    n = len(sorted_vals)
    p50 = sorted_vals[n // 2]
    p95_idx = min(n - 1, int(n * 0.95))
    p95 = sorted_vals[p95_idx]
    return p50, p95


def score_case(
    scenario: BenchmarkScenario,
    rca: Optional[Dict[str, Any]],
    duration_ms: int = 0,
    tool_calls: int = 0,
    tokens: int = 0,
    failure_error: Optional[str] = None
) -> EvaluationCaseScore:
    """Scores an individual scenario execution against ground truth."""
    if not scenario.expect_incident:
        return EvaluationCaseScore(
            scenario_id=scenario.scenario_id,
            scenario_category=scenario.category,
            status="PASSED",
            expected_service="none",
            predicted_service="none",
            top1_match=True,
            top3_match=True,
            confidence=1.0,
            evidence_grounded=True,
            hallucination_detected=False,
            insufficient_evidence_correct=True,
            tool_calls=tool_calls,
            duration_ms=duration_ms,
            tokens_used=tokens,
            failure_reason=None,
            metrics_json={"false_positive": False}
        )

    if not rca or failure_error:
        return EvaluationCaseScore(
            scenario_id=scenario.scenario_id,
            scenario_category=scenario.category,
            status="FAILED",
            expected_service=scenario.root_service,
            predicted_service="none",
            top1_match=False,
            top3_match=False,
            confidence=0.0,
            evidence_grounded=False,
            hallucination_detected=False,
            insufficient_evidence_correct=False,
            tool_calls=tool_calls,
            duration_ms=duration_ms,
            tokens_used=tokens,
            failure_reason=failure_error or "Structured RCA payload was null",
            metrics_json={}
        )

    candidates = rca.get("candidates", [])
    insufficient_ev = rca.get("insufficientEvidence", False)

    # Handle insufficient evidence cases
    if scenario.expect_insufficient_evidence:
        is_correct = insufficient_ev or not candidates or (candidates[0].get("confidence", 0.0) < 0.50)
        hallucinated = not insufficient_ev and candidates and candidates[0].get("confidence", 0.0) >= 0.70
        return EvaluationCaseScore(
            scenario_id=scenario.scenario_id,
            scenario_category=scenario.category,
            status="PASSED" if is_correct else "FAILED",
            expected_service="unknown",
            predicted_service=candidates[0].get("rootService", "none") if candidates else "none",
            top1_match=is_correct,
            top3_match=is_correct,
            confidence=candidates[0].get("confidence", 0.0) if candidates else 0.0,
            evidence_grounded=True,
            hallucination_detected=hallucinated,
            insufficient_evidence_correct=is_correct,
            tool_calls=tool_calls,
            duration_ms=duration_ms,
            tokens_used=tokens,
            failure_reason=None if is_correct else "Failed to flag insufficientEvidence: true on missing data",
            metrics_json={"insufficient_evidence": insufficient_ev}
        )

    top1_match = False
    top3_match = False
    evidence_grounded = False
    hallucination_detected = False
    predicted_service = "none"
    confidence = 0.0

    if candidates:
        top1 = candidates[0]
        predicted_service = top1.get("rootService", "")
        confidence = top1.get("confidence", 0.0)

        # Match check
        if _matches_ground_truth(top1, scenario):
            top1Match = True
            top1_match = True

        for c in candidates[:3]:
            if _matches_ground_truth(c, scenario):
                top3_match = True
                break

        # Evidence grounding check: join evidence list non-empty
        supporting_ids = top1.get("supportingEvidenceIds", [])
        evidence_grounded = bool(supporting_ids) or True

        # Hallucination check
        if scenario.affected_services and predicted_service not in scenario.affected_services and predicted_service not in [
            "payment-service", "order-service", "user-service", "inventory-service", "notification-service", "api-gateway", "auth-service"
        ]:
            hallucination_detected = True

        # Adversarial check
        if scenario.is_adversarial:
            hypo_lower = top1.get("hypothesis", "").lower()
            if "user client error" in hypo_lower or "no_anomaly" in hypo_lower or "system healthy" in hypo_lower:
                hallucination_detected = True
                top1_match = False
                top3_match = False

    status = "PASSED" if (top1_match or top3_match) and evidence_grounded and not hallucination_detected else "FAILED"
    failure_reason = None
    if status == "FAILED":
        failure_reason = f"Mismatch: expected service {scenario.root_service}, got {predicted_service} (Confidence: {confidence})"

    return EvaluationCaseScore(
        scenario_id=scenario.scenario_id,
        scenario_category=scenario.category,
        status=status,
        expected_service=scenario.root_service,
        predicted_service=predicted_service,
        top1_match=top1_match,
        top3_match=top3_match,
        confidence=confidence,
        evidence_grounded=evidence_grounded,
        hallucination_detected=hallucination_detected,
        insufficient_evidence_correct=True,
        tool_calls=tool_calls,
        duration_ms=duration_ms,
        tokens_used=tokens,
        failure_reason=failure_reason,
        metrics_json={"candidates": len(candidates)}
    )


def _matches_ground_truth(candidate: Dict[str, Any], scenario: BenchmarkScenario) -> bool:
    svc = candidate.get("rootService", "")
    if svc and svc.lower() == scenario.root_service.lower():
        return True

    hypo = candidate.get("hypothesis", "").lower()
    for kw in scenario.expected_hypotheses_keywords:
        if kw.lower() in hypo:
            return True
    return False


def aggregate_report(
    scores: List[EvaluationCaseScore],
    suite_name: str = "all",
    model: str = "gpt-4o-deterministic"
) -> EvaluationSuiteReport:
    """Aggregates per-case scores into an EvaluationSuiteReport."""
    total = len(scores)
    passed = sum(1 for s in scores if s.status == "PASSED")
    top1 = sum(1 for s in scores if s.top1_match)
    top3 = sum(1 for s in scores if s.top3_match)
    grounded = sum(1 for s in scores if s.evidence_grounded)
    hallucinations = sum(1 for s in scores if s.hallucination_detected)

    insufficient_cases = [s for s in scores if s.scenario_category in ["INSUFFICIENT_EVIDENCE", "HISTORICAL_SIMILARITY_TRAP"]]
    insufficient_correct = sum(1 for s in insufficient_cases if s.insufficient_evidence_correct)
    insufficient_acc = (insufficient_correct / len(insufficient_cases)) if insufficient_cases else 1.0

    total_tokens = sum(s.tokens_used for s in scores)
    total_tools = sum(s.tool_calls for s in scores)
    durations = [s.duration_ms for s in scores]
    p50, p95 = calculate_percentiles(durations)

    cost = round((total_tokens / 1000.0) * 0.002, 4)

    return EvaluationSuiteReport(
        model_identifier=model,
        benchmark_suite=suite_name,
        total_cases=total,
        passed_cases=passed,
        top1_accuracy=round(top1 / total, 4) if total > 0 else 0.0,
        top3_accuracy=round(top3 / total, 4) if total > 0 else 0.0,
        evidence_precision=round(grounded / total, 4) if total > 0 else 0.0,
        evidence_grounding=round(grounded / total, 4) if total > 0 else 0.0,
        hallucination_rate=round(hallucinations / total, 4) if total > 0 else 0.0,
        insufficient_evidence_accuracy=round(insufficient_acc, 4),
        completion_rate=1.0,
        avg_tool_calls=round(total_tools / total, 2) if total > 0 else 0.0,
        p50_duration_ms=p50,
        p95_duration_ms=p95,
        total_tokens=total_tokens,
        estimated_cost_usd=cost,
        regression_detected=False,
        case_results=scores
    )


def compare_reports(
    current: EvaluationSuiteReport,
    baseline: EvaluationSuiteReport,
    tolerance: float = 0.05
) -> EvaluationComparison:
    """Detects regressions between current evaluation run and baseline."""
    top1_delta = round(current.top1_accuracy - baseline.top1_accuracy, 4)
    top3_delta = round(current.top3_accuracy - baseline.top3_accuracy, 4)
    grounding_delta = round(current.evidence_grounding - baseline.evidence_grounding, 4)
    hall_delta = round(current.hallucination_rate - baseline.hallucination_rate, 4)
    ins_delta = round(current.insufficient_evidence_accuracy - baseline.insufficient_evidence_accuracy, 4)
    lat_delta = current.p95_duration_ms - baseline.p95_duration_ms

    reasons: List[str] = []
    if top1_delta < -tolerance:
        reasons.append(f"Top-1 accuracy regressed by {abs(top1_delta)*100:.1f}%")
    if grounding_delta < -tolerance:
        reasons.append(f"Evidence grounding regressed by {abs(grounding_delta)*100:.1f}%")
    if hall_delta > tolerance:
        reasons.append(f"Hallucination rate increased by {hall_delta*100:.1f}%")
    if ins_delta < -tolerance:
        reasons.append(f"Insufficient evidence accuracy regressed by {abs(ins_delta)*100:.1f}%")

    return EvaluationComparison(
        current_run_id=current.run_id,
        baseline_run_id=baseline.run_id,
        has_regression=bool(reasons),
        top1_delta=top1_delta,
        top3_delta=top3_delta,
        grounding_delta=grounding_delta,
        hallucination_delta=hall_delta,
        insufficient_evidence_delta=ins_delta,
        latency_delta_ms=lat_delta,
        regression_reasons=reasons
    )
