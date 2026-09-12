# ADR-004: OpenSearch for Logs and Trace Search

## Status
Accepted

## Context
ResolveIQ must store and query massive volumes of unstructured log events and distributed trace spans. The system requires fast full-text search, regex filtering, attribute queries across high-cardinality metadata, and aggregation capabilities for error-signature clustering.

## Decision
Adopt OpenSearch as the dedicated search and aggregation store for logs and trace spans.

## Alternatives Considered
1. **Grafana Loki**: Cost-effective for pure grep-like log queries, but provides inadequate ad-hoc aggregation flexibility and full-text index structures needed by the Evidence Builder for error-signature clustering.
2. **PostgreSQL / TimescaleDB**: Inefficient for large-scale full-text text search and arbitrarily nested JSON span attributes at target scale.
3. **Elasticsearch**: Functionally comparable, but carries restrictive licensing constraints compared to OpenSearch's Apache 2.0 license.

## Rationale
OpenSearch provides proven full-text search, flexible schema mappings, and multi-bucket aggregations essential for the Evidence Builder's log cluster detection and trace span parent-child link reconstruction.

## Architecture & Security Implications
- Sharding and index lifecycle management (ILM): Hot (14 days), cold object storage archive (90 days).
- Multi-tenancy: Tenant-scoped index aliases or document-level security plugin enforcing `tenant_id` on every query before execution.
- Failure handling: If OpenSearch encounters write rejection or red status, ingestion buffers safely in Kafka (7-day retention) rather than dropping data.

## Reconsideration Criteria
Revisit if per-tenant log storage costs become prohibitive relative to cold object-storage tiering, or if search features are not leveraged sufficiently to justify running the cluster.
