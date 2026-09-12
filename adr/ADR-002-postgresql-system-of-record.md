# ADR-002: PostgreSQL as System of Record

## Status
Accepted

## Context
ResolveIQ requires strongly consistent, relational, transactional storage for tenants, users, roles, permissions, projects, environments, services, dependencies, incidents, timeline events, evidence metadata, audit logs, API keys, and configuration.

## Decision
Adopt PostgreSQL as the single relational system of record for all tenant-scoped structured entities.

## Alternatives Considered
1. **NoSQL Document Stores (e.g., MongoDB, DynamoDB)**: Rejected due to weaker referential integrity, lack of native Row-Level Security (RLS), and the need to manually re-implement joins, foreign keys, and multi-entity ACID consistency at the application layer.
2. **Distributed SQL (e.g., CockroachDB, Spanner)**: High operational complexity and cost; unnecessary for the primary relational write workload at target scale when combined with TimescaleDB for metrics and OpenSearch for logs/traces.

## Rationale
- **Row-Level Security (RLS)**: Provides a structural, database-enforced multi-tenant isolation boundary independent of application code bugs.
- **ACID Transactions**: Guarantees integrity across incident state transitions, timeline additions, and audit events.
- **Ecosystem Unification**: PostgreSQL seamlessly extends with `TimescaleDB` (for time-series hypertables) and `pgvector` (for vector embeddings), drastically reducing operational surface area.

## Architecture & Security Implications
- RLS enabled on all tenant-scoped tables: `tenant_id = current_setting('app.tenant_id')::uuid`.
- Time-partitioning applied to high-volume tables (`audit_logs`, `incident_events`).
- Dedicated least-privileged database roles per service; application role has INSERT-only grants on `audit_logs`.

## Failure Modes & Recovery
- Primary failure triggers automated failover to streaming replica.
- Reads may degrade to read replica for dashboard listings, but never for authentication or tenant resolution.
- Idempotency keys used by callers to safely retry failed writes.

## Observability & Metrics
- Connection pool utilization (HikariCP / PgBouncer).
- Query execution latency and slow query logs (>100ms).
- Lock contention and replication lag.

## Reconsideration Criteria
Revisit if tenant-scoped transactional write latency cannot meet targets despite connection pooling, read replicas, and table partitioning.
