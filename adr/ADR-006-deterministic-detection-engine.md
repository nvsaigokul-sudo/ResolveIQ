# ADR-006: Rules/Statistics vs. ML vs. LLM for Incident Detection

## Status
Accepted

## Context
Incident detection must be reliable, fast, predictable, and available 24/7. An outage in the AI subsystem or LLM provider must never impair ResolveIQ's ability to ingest telemetry and detect ongoing outages.

## Decision
Incident detection is strictly implemented using deterministic rule-based and statistical algorithms (thresholds, EWMA, rolling-window variance, standard deviation bands, seasonality baselines, adaptive drift guardrails). Runtime LLM dependency in the detection path is explicitly prohibited.

## Alternatives Considered
1. **LLM-Based Anomaly Detection**: Prohibited. LLMs are non-deterministic, high-latency (seconds vs. milliseconds), expensive at 100K events/sec, prone to false alarms, and create a circular failure dependency if the LLM provider itself is degraded.
2. **Black-Box Deep Learning Anomaly Detection**: High false-positive rates, opaque threshold tuning, and difficult for SREs to understand why an alert was triggered under pressure.

## Rationale
- **High Availability**: If the AI model provider times out or fails, detection and correlation remain 100% operational.
- **Explainability**: Engineers can inspect exactly which metric breached which threshold over what time window.
- **Predictability**: Detection rules can be versioned, audited, and tuned deterministically.

## Reconsideration Criteria
Supervised statistical/ML models may only be incorporated if benchmarked with measurable precision/recall improvements over statistical baselines in the evaluation framework, and must remain completely decoupled from LLMs.
