# ADR-003: pgvector for Historical Knowledge Retrieval (RAG)

## Status
Accepted

## Context
ResolveIQ must provide semantic retrieval over historical incidents, postmortems, architecture docs, and runbooks to ground AI root-cause reasoning in prior institutional knowledge, with absolute multi-tenant isolation.

## Decision
Use the `pgvector` extension inside PostgreSQL, storing vector embeddings in a `knowledge_chunks` table protected by tenant-scoped Row-Level Security (RLS).

## Alternatives Considered
1. **Dedicated Vector Databases (e.g., Qdrant, Pinecone, Weaviate)**: Better raw approximate nearest neighbor (ANN) scaling for billions of vectors, but introduces an independent distributed cluster with its own separate multi-tenant isolation mechanism, separate backup/DR lifecycle, and risk of cross-tenant leakage.

## Rationale
- **Structural Multi-Tenancy**: With pgvector, tenant filtering is applied directly in the SQL `WHERE` clause and enforced via RLS *before* similarity ranking occurs. Cross-tenant candidate similarity computation is physically impossible.
- **Operational Simplicity**: Avoids operating another distributed data store for moderate per-tenant knowledge volumes (thousands to low millions of chunks).
- **Transactional Consistency**: Knowledge docs and vector chunks share transactional boundaries with the rest of the incident and project data.

## Architecture & Security Implications
- HNSW indexes partitioned by tenant / document type.
- Adversarial test suite in CI to verify 0% cross-tenant chunk leakage.
- Fallback mode: Vector search outage marks RAG similarity section unavailable; current telemetry investigation continues uninterrupted.

## Reconsideration Criteria
Revisit if per-tenant knowledge corpus exceeds ~5 million chunks or p95 retrieval latency exceeds 200ms despite HNSW indexing.
