# ADR-008: Dual-Mode Testing Strategy (Containerized + In-Process Embedded)

## Status
Accepted

## Context
Production and CI pipelines require testing against real external services (Kafka, PostgreSQL, TimescaleDB, OpenSearch) using Testcontainers and Docker Compose. However, local developer environments (such as Windows hosts without an active Docker daemon or restricted enterprise workstations) must still be capable of executing all unit, integration, and security verification tests quickly and reliably without external blockers.

## Decision
Adopt a **Dual-Mode Testing Architecture**:
1. **Containerized Mode**: Standard test profiles using Testcontainers for Kafka, PostgreSQL, OpenSearch, and TimescaleDB, executed in CI pipelines and environments with Docker available.
2. **In-Process Embedded Mode**: High-fidelity embedded fallbacks (Embedded Kafka via `spring-kafka-test`, embedded PostgreSQL / H2 PostgreSQL syntax mode, mock OpenSearch HTTP transport) automatically active when Docker is unavailable or in standalone local test runs.

## Alternatives Considered
1. **Docker-Only Mandate**: Fails immediately on environments without active container daemons; severely slows down local test-driven development loop.
2. **Pure Mocking Everywhere**: Violates PRD engineering contract (§68); fails to catch real database constraints, foreign keys, and serialization bugs.

## Rationale
Dual-mode execution gives the best of both worlds: rapid, zero-setup local verification on any workstation, combined with full containerized fidelity in CI quality gates.

## Reconsideration Criteria
Retire embedded fallbacks only if all developer machines and CI workers are guaranteed to have standardized rootless container runtimes with sub-second container spin-up times.
