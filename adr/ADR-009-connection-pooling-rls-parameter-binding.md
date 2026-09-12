# ADR-009: Connection Pooling & Postgres RLS Session Parameter Handling

## Status
Accepted

## Context
PostgreSQL Row-Level Security (RLS) policies rely on session parameters: `current_setting('app.tenant_id')::uuid`. In a production application utilizing connection pooling (HikariCP / PgBouncer), connections are reused across different HTTP requests and threads. If a session variable is set using `SET app.tenant_id = '...'`, the parameter persists on the physical connection, creating a critical risk of cross-tenant connection poisoning if a subsequent request reuses the connection without resetting the variable.

## Decision
Enforce transaction-scoped RLS variable setting using:
```sql
SELECT set_config('app.tenant_id', ?, true);
```
where the third parameter (`is_local = true`) instructs PostgreSQL to scope the setting strictly to the current transaction. Upon `COMMIT` or `ROLLBACK`, PostgreSQL automatically drops the session parameter.

In addition, an interceptor/filter checks that `TenantContext` is cleared from `ThreadLocal` in a `finally` block on every request completion.

## Alternatives Considered
1. **Global Session Variables (`SET app.tenant_id = ...`)**: Rejected due to high risk of cross-tenant leakage across pooled connection reuse.
2. **Dedicated DB Role / User per Tenant**: Prohibitive connection overhead and management complexity for thousands of tenants.

## Rationale
`set_config(..., true)` is atomic, transaction-scoped, and guaranteed by the PostgreSQL engine to reset upon transaction boundary conclusion. This prevents any possibility of connection pollution in connection pools like HikariCP.

## Reconsideration Criteria
None expected; this is the authoritative pattern for multi-tenant RLS with connection pooling.
