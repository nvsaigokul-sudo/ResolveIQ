"""
Data models for ResolveIQ AI Evaluation Framework (PRD §30, §56, §58).
"""

from typing import List, Dict, Optional, Any
from pydantic import BaseModel, Field
import uuid
from datetime import datetime, timezone


class BenchmarkScenario(BaseModel):
    scenario_id: str
    name: str
    category: str
    root_service: str
    affected_services: List[str] = Field(default_factory=list)
    expect_incident: bool = True
    expect_insufficient_evidence: bool = False
    expected_evidence_keywords: List[str] = Field(default_factory=list)
    expected_hypotheses_keywords: List[str] = Field(default_factory=list)
    min_confidence: float = 0.70
    is_adversarial: bool = False
    adversarial_payload: Optional[str] = None


class EvaluationCaseScore(BaseModel):
    scenario_id: str
    scenario_category: str
    status: str  # "PASSED", "FAILED", "REGRESSED"
    expected_service: str
    predicted_service: str
    top1_match: bool
    top3_match: bool
    confidence: float
    evidence_grounded: bool
    hallucination_detected: bool
    insufficient_evidence_correct: bool
    tool_calls: int = 0
    duration_ms: int = 0
    tokens_used: int = 0
    failure_reason: Optional[str] = None
    metrics_json: Dict[str, Any] = Field(default_factory=dict)


class EvaluationSuiteReport(BaseModel):
    run_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).isoformat())
    model_identifier: str = "gpt-4o-deterministic"
    benchmark_suite: str = "all"
    total_cases: int = 0
    passed_cases: int = 0
    top1_accuracy: float = 0.0
    top3_accuracy: float = 0.0
    evidence_precision: float = 0.0
    evidence_grounding: float = 0.0
    hallucination_rate: float = 0.0
    insufficient_evidence_accuracy: float = 0.0
    completion_rate: float = 1.0
    avg_tool_calls: float = 0.0
    p50_duration_ms: int = 0
    p95_duration_ms: int = 0
    total_tokens: int = 0
    estimated_cost_usd: float = 0.0
    regression_detected: bool = False
    case_results: List[EvaluationCaseScore] = Field(default_factory=list)


class EvaluationComparison(BaseModel):
    current_run_id: str
    baseline_run_id: str
    has_regression: bool
    top1_delta: float
    top3_delta: float
    grounding_delta: float
    hallucination_delta: float
    insufficient_evidence_delta: float
    latency_delta_ms: int
    regression_reasons: List[str] = Field(default_factory=list)
