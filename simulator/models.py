"""
Pydantic data models for ResolveIQ Incident Simulator (PRD §28, §29, §43).
Strongly typed models for scenario definitions, phase specifications, and OTLP wire payloads.
"""

from typing import List, Dict, Optional, Any
from enum import Enum
from pydantic import BaseModel, Field
import uuid


class MetricType(str, Enum):
    GAUGE = "GAUGE"
    COUNTER = "COUNTER"
    HISTOGRAM = "HISTOGRAM"


class LogSeverity(str, Enum):
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"
    FATAL = "FATAL"


class ServiceNode(BaseModel):
    name: str
    version: str = "v1.0"
    dependencies: List[str] = Field(default_factory=list)
    tier: str = "backend"
    environment: str = "production"


class MetricSpec(BaseModel):
    name: str
    type: MetricType = MetricType.GAUGE
    value: float
    labels: Dict[str, str] = Field(default_factory=dict)
    unit: Optional[str] = None


class LogSpec(BaseModel):
    severity: LogSeverity = LogSeverity.INFO
    pattern: str
    count: int = 1
    attributes: Dict[str, Any] = Field(default_factory=dict)


class TraceHopSpec(BaseModel):
    service: str
    operation: str
    duration_ms: int = 15
    status: str = "OK"
    attributes: Dict[str, Any] = Field(default_factory=dict)


class TraceSpec(BaseModel):
    entry_service: str
    entry_operation: str = "HTTP GET /api/v1/request"
    hops: List[TraceHopSpec] = Field(default_factory=list)
    error_service: Optional[str] = None
    error_message: Optional[str] = None


class DeploymentSpec(BaseModel):
    service: str
    version: str
    commit_sha: str
    commit_message: str
    author: str = "developer@resolveiq.io"
    offset_seconds: int = 0
    status: str = "SUCCESS"


class ConfigChangeSpec(BaseModel):
    service: str
    key: str
    old_value: str
    new_value: str
    offset_seconds: int = 0


class TelemetryPhase(BaseModel):
    name: str
    duration_seconds: int = 60
    request_rate: float = 100.0
    error_rate_percent: float = 0.0
    p50_latency_ms: float = 25.0
    p95_latency_ms: float = 80.0
    p99_latency_ms: float = 120.0
    cpu_percent: float = 35.0
    memory_percent: float = 50.0
    custom_metrics: List[MetricSpec] = Field(default_factory=list)
    logs: List[LogSpec] = Field(default_factory=list)
    traces: List[TraceSpec] = Field(default_factory=list)


class GroundTruthSpec(BaseModel):
    expected_incident: bool = True
    expected_severity: Optional[str] = "SEV1"
    root_service: Optional[str] = None
    root_cause_category: Optional[str] = None
    blast_radius_services: List[str] = Field(default_factory=list)
    expected_hypotheses: List[str] = Field(default_factory=list)
    min_confidence: float = 0.70
    explanation: str


class ScenarioDefinition(BaseModel):
    id: str
    name: str
    description: str
    category: str
    topology: List[ServiceNode]
    baseline_phase: TelemetryPhase
    fault_phase: TelemetryPhase
    recovery_phase: Optional[TelemetryPhase] = None
    deployment: Optional[DeploymentSpec] = None
    config_change: Optional[ConfigChangeSpec] = None
    ground_truth: GroundTruthSpec


# ============================================================================
# Canonical OpenTelemetry Wire Payloads (PRD §13, §15)
# ============================================================================

class OtlpMetricPayload(BaseModel):
    metric_name: str
    metric_type: str
    value: Optional[float] = None
    buckets: Dict[float, int] = Field(default_factory=dict)
    labels: Dict[str, str] = Field(default_factory=dict)
    service_name: str
    resource_attributes: Dict[str, str] = Field(default_factory=dict)
    timestamp: str


class OtlpMetricsBatch(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    service_name: str
    environment: str = "production"
    metrics: List[OtlpMetricPayload]
    resource_attributes: Dict[str, str] = Field(default_factory=dict)


class OtlpLogPayload(BaseModel):
    timestamp: str
    severity: str
    body: str
    attributes: Dict[str, Any] = Field(default_factory=dict)
    service_name: str
    deployment_environment: str = "production"
    trace_id: Optional[str] = None
    span_id: Optional[str] = None
    resource_attributes: Dict[str, str] = Field(default_factory=dict)


class OtlpLogsBatch(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    service_name: str
    environment: str = "production"
    logs: List[OtlpLogPayload]
    resource_attributes: Dict[str, str] = Field(default_factory=dict)


class OtlpSpanPayload(BaseModel):
    trace_id: str
    span_id: str
    parent_span_id: Optional[str] = None
    service_name: str
    operation_name: str
    start_time: str
    duration_ms: int
    status: str = "OK"
    attributes: Dict[str, Any] = Field(default_factory=dict)
    resource_attributes: Dict[str, str] = Field(default_factory=dict)


class OtlpTracesBatch(BaseModel):
    event_id: str = Field(default_factory=lambda: str(uuid.uuid4()))
    service_name: str
    environment: str = "production"
    spans: List[OtlpSpanPayload]
    resource_attributes: Dict[str, str] = Field(default_factory=dict)
