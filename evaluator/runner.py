"""
CLI runner and CI/CD benchmark harness for ResolveIQ AI Evaluation Framework (PRD §30, §56, §58).
"""

import sys
import json
import argparse
import requests
from pathlib import Path
from typing import List, Dict, Any, Optional

from .models import BenchmarkScenario, EvaluationCaseScore, EvaluationSuiteReport, EvaluationComparison
from .metrics import score_case, aggregate_report, compare_reports


def run_benchmark_cli(
    backend_url: str,
    api_key: str,
    tenant_id: str,
    suite: str = "all",
    baseline_path: Optional[str] = None,
    output_json: Optional[str] = None,
    dry_run: bool = False
) -> int:
    """Executes evaluation suite via backend REST API or dry-run evaluation."""
    print(f"\n=======================================================")
    print(f"ResolveIQ AI Evaluation Framework (Suite: {suite})")
    print(f"=======================================================\n")

    if dry_run:
        print("[DRY-RUN] Simulating benchmark scoring on canonical test cases...")
        dummy_scores = [
            EvaluationCaseScore(
                scenario_id="scenario-01-bad-deployment",
                scenario_category="DEPLOYMENT_REGRESSION",
                status="PASSED",
                expected_service="payment-service",
                predicted_service="payment-service",
                top1_match=True,
                top3_match=True,
                confidence=0.84,
                evidence_grounded=True,
                hallucination_detected=False,
                insufficient_evidence_correct=True,
                tool_calls=4,
                duration_ms=280,
                tokens_used=1250,
                failure_reason=None
            ),
            EvaluationCaseScore(
                scenario_id="scenario-06-legitimate-traffic-spike",
                scenario_category="BENIGN_TRAFFIC_SURGE",
                status="PASSED",
                expected_service="none",
                predicted_service="none",
                top1_match=True,
                top3_match=True,
                confidence=1.0,
                evidence_grounded=True,
                hallucination_detected=False,
                insufficient_evidence_correct=True,
                tool_calls=0,
                duration_ms=10,
                tokens_used=0,
                failure_reason=None
            ),
            EvaluationCaseScore(
                scenario_id="scenario-11-insufficient-evidence",
                scenario_category="INSUFFICIENT_EVIDENCE",
                status="PASSED",
                expected_service="unknown",
                predicted_service="none",
                top1_match=True,
                top3_match=True,
                confidence=0.0,
                evidence_grounded=True,
                hallucination_detected=False,
                insufficient_evidence_correct=True,
                tool_calls=2,
                duration_ms=150,
                tokens_used=600,
                failure_reason=None
            )
        ]
        report = aggregate_report(dummy_scores, suite_name=suite)
    else:
        url = f"{backend_url.rstrip('/')}/api/v1/evaluations/run"
        headers = {
            "Content-Type": "application/json",
            "X-API-Key": api_key,
            "X-Tenant-Id": tenant_id
        }
        payload = {"benchmarkSuite": suite}
        try:
            res = requests.post(url, json=payload, headers=headers, timeout=120)
            res.raise_for_status()
            raw_data = res.json().get("data", {})
            report = EvaluationSuiteReport.model_validate(raw_data)
        except Exception as ex:
            print(f"Error calling backend evaluation endpoint: {ex}")
            return 1

    # Print Report Summary
    print_report(report)

    # Compare with baseline if provided
    if baseline_path:
        b_path = Path(baseline_path)
        if b_path.exists():
            with open(b_path, "r", encoding="utf-8") as f:
                baseline_report = EvaluationSuiteReport.model_validate(json.load(f))
            comp = compare_reports(report, baseline_report)
            print_comparison(comp)
            if comp.has_regression:
                print("\n\033[91m[FAILURE] Regression Gate Tripped! Regressions detected.\033[0m")
                return 1
        else:
            print(f"[WARN] Baseline report path {baseline_path} does not exist. Skipping regression check.")

    if output_json:
        out_p = Path(output_json)
        out_p.parent.mkdir(parents=True, exist_ok=True)
        with open(out_p, "w", encoding="utf-8") as f:
            f.write(report.model_dump_json(indent=2))
        print(f"\nSaved evaluation report JSON to: {out_p}")

    return 0


def print_report(r: EvaluationSuiteReport):
    print(f"Run ID:                    {r.run_id}")
    print(f"Model:                     {r.model_identifier}")
    print(f"Benchmark Suite:           {r.benchmark_suite}")
    print(f"Total Cases:               {r.total_cases}")
    print(f"Passed Cases:              {r.passed_cases} ({(r.passed_cases/max(1, r.total_cases))*100:.1f}%)")
    print(f"Top-1 RCA Accuracy:        {r.top1_accuracy * 100:.2f}%")
    print(f"Top-3 RCA Accuracy:        {r.top3_accuracy * 100:.2f}%")
    print(f"Evidence Grounding:        {r.evidence_grounding * 100:.2f}%")
    print(f"Hallucination Rate:        {r.hallucination_rate * 100:.2f}%")
    print(f"Insufficient Ev. Accuracy: {r.insufficient_evidence_accuracy * 100:.2f}%")
    print(f"Avg Tool Calls:            {r.avg_tool_calls:.2f}")
    print(f"p50 / p95 Latency:         {r.p50_duration_ms}ms / {r.p95_duration_ms}ms")
    print(f"Total Tokens / Est. Cost:  {r.total_tokens} tokens / ${r.estimated_cost_usd:.4f}")
    print("\nPer-Scenario Breakdown:")
    print("-" * 88)
    print(f"{'SCENARIO ID':<36} | {'STATUS':<8} | {'TOP-1':<6} | {'GROUNDED':<8} | {'CONF':<6}")
    print("-" * 88)
    for c in r.case_results:
        t1_str = "YES" if c.top1_match else "NO"
        gr_str = "YES" if c.evidence_grounded else "NO"
        print(f"{c.scenario_id:<36} | {c.status:<8} | {t1_str:<6} | {gr_str:<8} | {c.confidence:.2f}")
    print("-" * 88)


def print_comparison(comp: EvaluationComparison):
    print("\n-------------------------------------------------------")
    print("Regression Comparison against Baseline:")
    print("-------------------------------------------------------")
    print(f"Top-1 Delta:        {comp.top1_delta * 100:+.2f}%")
    print(f"Top-3 Delta:        {comp.top3_delta * 100:+.2f}%")
    print(f"Grounding Delta:    {comp.grounding_delta * 100:+.2f}%")
    print(f"Hallucination Delta:{comp.hallucination_delta * 100:+.2f}%")
    print(f"Latency Delta:      {comp.latency_delta_ms:+d}ms")
    if comp.has_regression:
        print("\nIdentified Regressions:")
        for r in comp.regression_reasons:
            print(f"  - {r}")
    else:
        print("\n\033[92m[PASSED] No regressions detected against baseline.\033[0m")


def main():
    parser = argparse.ArgumentParser(description="ResolveIQ AI Evaluation Runner (PRD §30, §56)")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--api-key", default="riq_live_demo_api_key_for_simulation")
    parser.add_argument("--tenant", default="00000000-0000-0000-0000-000000000001")
    parser.add_argument("--suite", default="all")
    parser.add_argument("--baseline", default=None, help="Path to baseline evaluation JSON")
    parser.add_argument("-o", "--output", default=None, help="Path to save evaluation report JSON")
    parser.add_argument("--dry-run", action="store_true", help="Execute dry-run without backend network calls")

    args = parser.parse_args()
    code = run_benchmark_cli(
        backend_url=args.backend_url,
        api_key=args.api_key,
        tenant_id=args.tenant,
        suite=args.suite,
        baseline_path=args.baseline,
        output_json=args.output,
        dry_run=args.dry_run
    )
    sys.exit(code)


if __name__ == "__main__":
    main()
