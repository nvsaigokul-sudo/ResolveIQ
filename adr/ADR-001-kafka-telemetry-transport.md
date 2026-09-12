# ADR-001: Apache Kafka for Telemetry Transport

## Status
Accepted

## Context
Production telemetry (metrics, logs, traces) arrives in unpredictable bursts, especially during deployments, sudden traffic surges, and cascading failure cascades. Downstream consumers (processors, detection engine, database writers) must not be tightly coupled to the availability, throughput, or latency of producers.

## Decision
Adopt Apache Kafka as the single asynchronous event backbone between the Ingestion Service and downstream consumers (telemetry processors, detection engine, correlation engine, and notification dispatcher).

## Alternatives Considered
1. **Direct Synchronous REST/HTTP Calls**: Rejected because synchronous chaining creates cascading failure domains, lacks durable buffering, and offers no backpressure management under load spikes.
2. **AWS SQS/SNS**: Rejected because it lacks ordered replay-by-key guarantees required for time-windowed metric detection, has weaker stream processing ecosystems, and increases vendor lock-in.
3. **RabbitMQ**: Rejected because it is less optimized for high-throughput sequential log/metric replay and multi-day retention.

## Rationale
Kafka provides durable multi-day retention (enabling replaying historical windows through processors after bug fixes), horizontal consumer-group scaling, natural backpressure (producers do not block slow consumers), and per-key partition ordering.

## Architecture & Security Implications
- **Partition Key**: `tenant_id + ":" + service_id` to guarantee per-service chronological ordering while allowing even horizontal distribution across consumers.
- **Tenant Context**: Partition keys and message envelopes carry authenticated `tenant_id`. No tenant identity is trusted from raw payload bodies alone.
- **Security**: Topic-level ACLs, mTLS between services and brokers, payload encryption.

## Failure Modes & Recovery
- **Broker Unavailability**: Ingestion service buffers briefly into a bounded local queue, then applies backpressure by rejecting incoming telemetry with `503 Service Unavailable` and a `Retry-After` header. No silent data drop.
- **Consumer Lag Growth**: Monitored via consumer lag metrics; triggers automated consumer scaling.
- **Poison Messages**: Routed to dead-letter topics (`<topic>.DLQ`) after $N$ retries with exception details attached.

## Observability & Metrics
- Consumer lag per group and topic partition (`kafka_consumer_lag_records`).
- Produce/consume rates and batch latencies (`kafka_producer_latency_ms`).
- DLQ message depth.

## Reconsideration Criteria
Revisit if sustained ingestion throughput requirements exceed what a cost-effective Kafka cluster can serve, or if a fully managed open standard streaming platform reduces operational overhead without sacrificing partition-key ordering or 7-day replay guarantees.
