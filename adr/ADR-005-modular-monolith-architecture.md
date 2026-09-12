# ADR-005: Modular Monolith vs. Microservices for Core Domains

## Status
Accepted

## Context
ResolveIQ contains several core operational domains: organization/RBAC, incident management, timeline tracking, evidence collection, detection rules, correlation logic, dynamic dependency graph, and audit logging. Premature microservice decomposition introduces distributed transaction complexity, network serialization latency, and complex deployment coordination.

## Decision
Implement core domains as a **Modular Monolith** in a single deployable Spring Boot application (`backend/`), enforcing strict package-level module boundaries with ArchUnit architecture tests. Extract only independently scalable/isolated services:
1. `ingestion/` (OTLP receiver → Kafka producer)
2. `processors/` (Kafka telemetry consumers)
3. `ai-investigation/` (Spring AI agent & tools)
4. `notifications/` (Provider dispatch & DLQ)
5. `frontend/` (Next.js 14)

## Alternatives Considered
1. **Full Microservices Architecture (10+ services)**: Premature; adds network latency to internal incident-evidence workflows and requires distributed 2PC or Saga patterns across transactional incident updates.
2. **Unstructured Single Monolith**: High coupling risk; makes future extraction difficult and conflates CPU-intensive telemetry I/O with incident workflow logic.

## Rationale
The modular monolith keeps incident, evidence, correlation, and audit updates within transactional boundaries, while independently deploying bursty I/O ingestion and latency-heavy AI workflows. Package-level module boundaries are enforced by architecture tests so any submodule can be extracted later if scaling profiles diverge.

## Reconsideration Criteria
Extract an individual module from `backend/` into a standalone service only when its latency, scaling, or failure-isolation profile measurably diverges from the monolith.
