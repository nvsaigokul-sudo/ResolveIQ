"""
Telemetry generator for ResolveIQ Incident Simulator (PRD §28, §29).
Produces canonical OpenTelemetry metrics, logs, and distributed trace spans matching
the real customer telemetry schema (PRD §13, §15).
"""

import time
import uuid
import random
from datetime import datetime, timezone
from typing import List, Dict, Tuple, Optional, Any

from .models import (
    ScenarioDefinition, TelemetryPhase, ServiceNode,
    OtlpMetricsBatch, OtlpMetricPayload,
    OtlpLogsBatch, OtlpLogPayload,
    OtlpTracesBatch, OtlpSpanPayload
)


class TelemetryGenerator:
    """
    Generates realistic OpenTelemetry batches for a given scenario phase.
    Ensures that distributed trace IDs and parent-child spans correctly reflect
    the service topology, and metric/log patterns match the fault injection profile.
    """

    def __init__(self, tenant_id: str, environment: str = "production"):
        self.tenant_id = tenant_id
        self.environment = environment

    def _now_iso(self, offset_seconds: float = 0.0) -> str:
        t = datetime.now(timezone.utc).timestamp() + offset_seconds
        return datetime.fromtimestamp(t, timezone.utc).isoformat().replace("+00:00", "Z")

    def generate_metrics(
        self,
        service: ServiceNode,
        phase: TelemetryPhase,
        timestamp_offset_sec: float = 0.0
    ) -> OtlpMetricsBatch:
        """Generates canonical metric gauges, counters, and histograms."""
        ts = self._now_iso(timestamp_offset_sec)
        metrics: List[OtlpMetricPayload] = []

        res_attrs = {
            "service.name": service.name,
            "service.version": service.version,
            "deployment.environment": self.environment,
            "tenant.id": self.tenant_id,
        }

        # 1. HTTP Server Latency (Duration)
        latency_val = phase.p99_latency_ms if random.random() < 0.1 else phase.p50_latency_ms
        latency_val += random.uniform(-5.0, 5.0)
        latency_val = max(1.0, latency_val)

        metrics.append(OtlpMetricPayload(
            metric_name="http.server.duration",
            metric_type="HISTOGRAM",
            value=round(latency_val, 2),
            buckets={
                50.0: 100,
                100.0: 80,
                250.0: int(phase.request_rate * 0.9),
                500.0: int(phase.request_rate * 0.95),
                1000.0: int(phase.request_rate * (0.98 if phase.error_rate_percent > 5 else 1.0)),
                2500.0: int(phase.request_rate),
            },
            labels={"http.method": "POST", "http.route": f"/api/{service.name}/process"},
            service_name=service.name,
            resource_attributes=res_attrs,
            timestamp=ts
        ))

        # 2. HTTP Server Request / Error Rate
        error_count = int(phase.request_rate * (phase.error_rate_percent / 100.0))
        success_count = int(phase.request_rate) - error_count

        metrics.append(OtlpMetricPayload(
            metric_name="http.server.requests",
            metric_type="COUNTER",
            value=float(success_count),
            labels={"http.status_code": "200"},
            service_name=service.name,
            resource_attributes=res_attrs,
            timestamp=ts
        ))

        if error_count > 0:
            status_code = "503" if "outage" in phase.name.lower() else "500"
            metrics.append(OtlpMetricPayload(
                metric_name="http.server.requests",
                metric_type="COUNTER",
                value=float(error_count),
                labels={"http.status_code": status_code},
                service_name=service.name,
                resource_attributes=res_attrs,
                timestamp=ts
            ))

        # 3. CPU and Memory Utilization
        cpu_val = min(100.0, max(5.0, phase.cpu_percent + random.uniform(-2.0, 2.0)))
        mem_val = min(100.0, max(10.0, phase.memory_percent + random.uniform(-1.0, 1.0)))

        metrics.append(OtlpMetricPayload(
            metric_name="system.cpu.utilization",
            metric_type="GAUGE",
            value=round(cpu_val, 2),
            labels={"host.id": f"host-{service.name}-1"},
            service_name=service.name,
            resource_attributes=res_attrs,
            timestamp=ts
        ))

        metrics.append(OtlpMetricPayload(
            metric_name="jvm.memory.used",
            metric_type="GAUGE",
            value=round(mem_val, 2),
            labels={"area": "heap"},
            service_name=service.name,
            resource_attributes=res_attrs,
            timestamp=ts
        ))

        # 4. Scenario-Specific Custom Metrics
        for cm in phase.custom_metrics:
            val = cm.value + random.uniform(-cm.value * 0.05, cm.value * 0.05) if cm.value > 0 else 0.0
            metrics.append(OtlpMetricPayload(
                metric_name=cm.name,
                metric_type=cm.type.value,
                value=round(val, 2),
                labels=cm.labels,
                service_name=service.name,
                resource_attributes=res_attrs,
                timestamp=ts
            ))

        return OtlpMetricsBatch(
            event_id=str(uuid.uuid4()),
            service_name=service.name,
            environment=self.environment,
            metrics=metrics,
            resource_attributes=res_attrs
        )

    def generate_logs(
        self,
        service: ServiceNode,
        phase: TelemetryPhase,
        trace_id: Optional[str] = None,
        span_id: Optional[str] = None,
        timestamp_offset_sec: float = 0.0
    ) -> OtlpLogsBatch:
        """Generates structured log entries with error patterns and stack traces."""
        ts = self._now_iso(timestamp_offset_sec)
        logs: List[OtlpLogPayload] = []

        res_attrs = {
            "service.name": service.name,
            "service.version": service.version,
            "deployment.environment": self.environment,
            "tenant.id": self.tenant_id,
        }

        # Healthy INFO logs
        logs.append(OtlpLogPayload(
            timestamp=ts,
            severity="INFO",
            body=f"[{service.name}] Handled request successfully. Status 200 OK",
            attributes={"component": "http-handler", "request.path": f"/{service.name}/api"},
            service_name=service.name,
            deployment_environment=self.environment,
            trace_id=trace_id,
            span_id=span_id,
            resource_attributes=res_attrs
        ))

        # Fault phase logs specified in scenario YAML
        for log_spec in phase.logs:
            for _ in range(log_spec.count):
                tid = trace_id or uuid.uuid4().hex
                sid = span_id or uuid.uuid4().hex[:16]
                logs.append(OtlpLogPayload(
                    timestamp=ts,
                    severity=log_spec.severity.value,
                    body=log_spec.pattern,
                    attributes={**log_spec.attributes, "service": service.name},
                    service_name=service.name,
                    deployment_environment=self.environment,
                    trace_id=tid,
                    span_id=sid,
                    resource_attributes=res_attrs
                ))

        return OtlpLogsBatch(
            event_id=str(uuid.uuid4()),
            service_name=service.name,
            environment=self.environment,
            logs=logs,
            resource_attributes=res_attrs
        )

    def generate_traces(
        self,
        scenario: ScenarioDefinition,
        phase: TelemetryPhase,
        timestamp_offset_sec: float = 0.0
    ) -> List[OtlpTracesBatch]:
        """
        Generates distributed trace span DAGs traversing the scenario topology.
        Ensures consistent trace_id across services and links parent/child span IDs.
        """
        batches: List[OtlpTracesBatch] = []
        services_by_name = {s.name: s for s in scenario.topology}

        # Identify root service(s) (in-degree == 0, e.g. api-gateway)
        all_deps = set()
        for s in scenario.topology:
            all_deps.update(s.dependencies)
        root_services = [s for s in scenario.topology if s.name not in all_deps]
        if not root_services:
            root_services = [scenario.topology[0]]

        # Generate N distributed traces per cycle
        trace_count = 3
        for _ in range(trace_count):
            trace_id = uuid.uuid4().hex
            trace_spans_by_service: Dict[str, List[OtlpSpanPayload]] = {s.name: [] for s in scenario.topology}

            for root_svc in root_services:
                self._recurse_trace_spans(
                    root_svc,
                    services_by_name,
                    phase,
                    trace_id=trace_id,
                    parent_span_id=None,
                    spans_by_service=trace_spans_by_service,
                    timestamp_offset_sec=timestamp_offset_sec
                )

            for svc_name, spans in trace_spans_by_service.items():
                if spans:
                    svc = services_by_name[svc_name]
                    batches.append(OtlpTracesBatch(
                        event_id=str(uuid.uuid4()),
                        service_name=svc_name,
                        environment=self.environment,
                        spans=spans,
                        resource_attributes={
                            "service.name": svc.name,
                            "service.version": svc.version,
                            "deployment.environment": self.environment,
                            "tenant.id": self.tenant_id,
                        }
                    ))

        return batches

    def _recurse_trace_spans(
        self,
        current_svc: ServiceNode,
        services_by_name: Dict[str, ServiceNode],
        phase: TelemetryPhase,
        trace_id: str,
        parent_span_id: Optional[str],
        spans_by_service: Dict[str, List[OtlpSpanPayload]],
        timestamp_offset_sec: float
    ):
        span_id = uuid.uuid4().hex[:16]
        ts = self._now_iso(timestamp_offset_sec)

        is_error = False
        duration_ms = int(phase.p50_latency_ms + random.uniform(2, 10))

        # Check if current service is experiencing fault
        if phase.error_rate_percent > 0:
            if random.random() < (phase.error_rate_percent / 100.0):
                is_error = True
                duration_ms = int(phase.p99_latency_ms + random.uniform(50, 200))

        span = OtlpSpanPayload(
            trace_id=trace_id,
            span_id=span_id,
            parent_span_id=parent_span_id,
            service_name=current_svc.name,
            operation_name=f"POST /api/v1/{current_svc.name}",
            start_time=ts,
            duration_ms=duration_ms,
            status="ERROR" if is_error else "OK",
            attributes={
                "http.status_code": 500 if is_error else 200,
                "error": is_error,
                "service.tier": current_svc.tier
            },
            resource_attributes={
                "service.name": current_svc.name,
                "service.version": current_svc.version,
                "deployment.environment": self.environment,
                "tenant.id": self.tenant_id,
            }
        )

        spans_by_service[current_svc.name].append(span)

        # Propagate to downstream dependencies
        for dep_name in current_svc.dependencies:
            if dep_name in services_by_name:
                self._recurse_trace_spans(
                    services_by_name[dep_name],
                    services_by_name,
                    phase,
                    trace_id=trace_id,
                    parent_span_id=span_id,
                    spans_by_service=spans_by_service,
                    timestamp_offset_sec=timestamp_offset_sec + (duration_ms / 1000.0)
                )
