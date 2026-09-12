# ADR-007: Multi-Tenant SaaS Isolation Strategy

## Status
Accepted

## Context
ResolveIQ is designed as a multi-tenant SaaS platform where multiple organizations share computing infrastructure. Cross-tenant data leakage is classified as a critical security vulnerability and release-blocking defect.

## Decision
Enforce a defense-in-depth 8-layer logical multi-tenancy model rather than physical multi-instance infrastructure. Tenant identity is resolved exclusively server-side from cryptographic credentials (JWT claims or salted Argon2id API keys); client-supplied headers (e.g. `X-Tenant-Id`) are strictly ignored.

## Alternatives Considered
1. **Physical Tenancy (Separate DB/Cluster per Tenant)**: Operationally prohibitive, slow tenant provisioning, high cloud infrastructure costs.
2. **Schema-per-Tenant**: Migrations become unmanageable as tenant count grows; schema explosion degrades Postgres performance.
3. **Application-Only Tenant Filtering (`WHERE tenant_id = ?`)**: Vulnerable to developer error, forgotten where clauses, or framework bugs.

## Rationale
The 8-layer defense in depth combines:
1. Application authorization (`TenantContext`)
2. Repository query injection
3. PostgreSQL Row-Level Security (RLS) as structural backstop
4. Tenant-scoped Kafka metadata and partition keys
5. pgvector pre-ranking RLS filtering
6. Tenant-namespaced caching (`tenant:{id}:`)
7. Per-tenant rate limits & quotas
8. CI release gate cross-tenant isolation test suite

## Reconsideration Criteria
Physical isolation is out of scope for v1.0. A single-tenant enterprise deployment model would require dedicated architectural derivation and is not achievable merely by configuration flags.
