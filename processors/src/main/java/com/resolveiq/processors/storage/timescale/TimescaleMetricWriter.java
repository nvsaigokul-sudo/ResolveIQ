package com.resolveiq.processors.storage.timescale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * High-throughput, tenant-isolated batch writer and query service for TimescaleDB metrics.
 * Supports raw metrics ingestion, idempotent conflict resolution, and aggregate rollups (PRD §10.3, §13.2).
 */
@Repository
public class TimescaleMetricWriter {

    private static final Logger log = LoggerFactory.getLogger(TimescaleMetricWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean isPostgres;

    public TimescaleMetricWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.isPostgres = checkIsPostgres(dataSource);
        log.info("TimescaleMetricWriter initialized with database mode: {}", isPostgres ? "PostgreSQL/TimescaleDB" : "Standard/H2");
    }

    private boolean checkIsPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName();
            return productName != null && productName.toLowerCase().contains("postgres");
        } catch (SQLException e) {
            log.warn("Could not determine database product name, defaulting to PostgreSQL mode", e);
            return true;
        }
    }

    /**
     * Computes a deterministic SHA-256 hash of metric dimension keys and values.
     */
    public static String computeDimensionsHash(Map<String, String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return "empty";
        }
        List<String> sortedKeys = new ArrayList<>(dimensions.keySet());
        Collections.sort(sortedKeys);
        StringBuilder sb = new StringBuilder();
        for (String k : sortedKeys) {
            sb.append(k).append('=').append(dimensions.get(k)).append(';');
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    /**
     * Batch inserts metric records with idempotent ON CONFLICT DO NOTHING resolution.
     *
     * @param records list of normalized metric records
     * @return count of rows processed
     */
    public int insertBatch(List<MetricRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }

        String sql = isPostgres ?
                "INSERT INTO metrics_raw (" +
                        "time, tenant_id, project_id, service_id, environment, " +
                        "metric_name, metric_type, metric_value, unit, " +
                        "dimensions, dimensions_hash, event_id, is_late, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?) " +
                        "ON CONFLICT (time, tenant_id, service_id, metric_name, dimensions_hash, event_id) DO NOTHING"
                :
                "MERGE INTO metrics_raw (" +
                        "time, tenant_id, project_id, service_id, environment, " +
                        "metric_name, metric_type, metric_value, unit, " +
                        "dimensions, dimensions_hash, event_id, is_late, created_at) " +
                        "KEY(time, tenant_id, service_id, metric_name, dimensions_hash, event_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int[][] result = jdbcTemplate.batchUpdate(sql, records, records.size(), (PreparedStatement ps, MetricRecord record) -> {
            ps.setTimestamp(1, Timestamp.from(record.time()));
            ps.setObject(2, record.tenantId());
            ps.setObject(3, record.projectId());
            ps.setString(4, record.serviceId());
            ps.setString(5, record.environment() != null ? record.environment() : "production");
            ps.setString(6, record.metricName());
            ps.setString(7, record.metricType());
            ps.setDouble(8, record.metricValue());
            ps.setString(9, record.unit());

            String dimensionsJson = "{}";
            try {
                if (record.dimensions() != null && !record.dimensions().isEmpty()) {
                    dimensionsJson = objectMapper.writeValueAsString(record.dimensions());
                }
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize dimensions JSON for metric {}", record.metricName(), e);
            }
            ps.setString(10, dimensionsJson);

            String hash = record.dimensionsHash() != null ? record.dimensionsHash() : computeDimensionsHash(record.dimensions());
            ps.setString(11, hash);
            ps.setObject(12, record.eventId());
            ps.setBoolean(13, record.isLate());
            ps.setTimestamp(14, Timestamp.from(record.createdAt() != null ? record.createdAt() : Instant.now()));
        });

        int totalInserted = 0;
        for (int[] batch : result) {
            for (int count : batch) {
                if (count > 0) totalInserted += count;
            }
        }
        return totalInserted;
    }

    /**
     * Queries raw metrics with strict tenant isolation (Layer 1 + Layer 2 defense-in-depth).
     */
    public List<MetricRecord> queryMetrics(UUID tenantId, String serviceId, String metricName, Instant startTime, Instant endTime) {
        String sql = "SELECT time, tenant_id, project_id, service_id, environment, " +
                "metric_name, metric_type, metric_value, unit, " +
                "dimensions, dimensions_hash, event_id, is_late, created_at " +
                "FROM metrics_raw " +
                "WHERE tenant_id = ? AND service_id = ? AND metric_name = ? " +
                "AND time >= ? AND time <= ? " +
                "ORDER BY time ASC";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, String> dimensions = Collections.emptyMap();
            String dimJson = rs.getString("dimensions");
            if (dimJson != null && !dimJson.isBlank()) {
                try {
                    dimensions = objectMapper.readValue(dimJson, Map.class);
                } catch (Exception ignored) {}
            }

            return new MetricRecord(
                    rs.getTimestamp("time").toInstant(),
                    rs.getObject("tenant_id", UUID.class),
                    rs.getObject("project_id", UUID.class),
                    rs.getString("service_id"),
                    rs.getString("environment"),
                    rs.getString("metric_name"),
                    rs.getString("metric_type"),
                    rs.getDouble("metric_value"),
                    rs.getString("unit"),
                    dimensions,
                    rs.getString("dimensions_hash"),
                    rs.getObject("event_id", UUID.class),
                    rs.getBoolean("is_late"),
                    rs.getTimestamp("created_at").toInstant()
            );
        }, tenantId, serviceId, metricName, Timestamp.from(startTime), Timestamp.from(endTime));
    }

    /**
     * Rollup aggregated point model.
     */
    public record RollupPoint(
            Instant bucket,
            UUID tenantId,
            String serviceId,
            String metricName,
            long sampleCount,
            double valMin,
            double valMax,
            double valAvg,
            double valSum
    ) {}

    /**
     * Queries rollup continuous aggregates (e.g. 1m, 5m, 1h, 1d) with fallback to dynamic aggregation.
     */
    public List<RollupPoint> queryRollup(UUID tenantId, String serviceId, String metricName, String resolution, Instant startTime, Instant endTime) {
        String tableName = switch (resolution.toLowerCase()) {
            case "1m" -> "metrics_1m";
            case "5m" -> "metrics_5m";
            case "1h" -> "metrics_1h";
            case "1d" -> "metrics_1d";
            default -> null;
        };

        if (tableName != null && isPostgres) {
            try {
                String sql = "SELECT bucket, tenant_id, service_id, metric_name, sample_count, val_min, val_max, val_avg, val_sum " +
                        "FROM " + tableName + " " +
                        "WHERE tenant_id = ? AND service_id = ? AND metric_name = ? " +
                        "AND bucket >= ? AND bucket <= ? ORDER BY bucket ASC";

                return jdbcTemplate.query(sql, (rs, rowNum) -> new RollupPoint(
                        rs.getTimestamp("bucket").toInstant(),
                        rs.getObject("tenant_id", UUID.class),
                        rs.getString("service_id"),
                        rs.getString("metric_name"),
                        rs.getLong("sample_count"),
                        rs.getDouble("val_min"),
                        rs.getDouble("val_max"),
                        rs.getDouble("val_avg"),
                        rs.getDouble("val_sum")
                ), tenantId, serviceId, metricName, Timestamp.from(startTime), Timestamp.from(endTime));
            } catch (Exception e) {
                log.debug("Continuous aggregate view {} not available, falling back to dynamic rollup", tableName);
            }
        }

        // Dynamic rollup calculation fallback (used for tests or when matview is refreshing)
        String dynamicSql = "SELECT time AS bucket, tenant_id, service_id, metric_name, " +
                "COUNT(*) AS sample_count, MIN(metric_value) AS val_min, MAX(metric_value) AS val_max, " +
                "AVG(metric_value) AS val_avg, SUM(metric_value) AS val_sum " +
                "FROM metrics_raw " +
                "WHERE tenant_id = ? AND service_id = ? AND metric_name = ? " +
                "AND time >= ? AND time <= ? " +
                "GROUP BY time, tenant_id, service_id, metric_name ORDER BY bucket ASC";

        return jdbcTemplate.query(dynamicSql, (rs, rowNum) -> new RollupPoint(
                rs.getTimestamp("bucket").toInstant(),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("service_id"),
                rs.getString("metric_name"),
                rs.getLong("sample_count"),
                rs.getDouble("val_min"),
                rs.getDouble("val_max"),
                rs.getDouble("val_avg"),
                rs.getDouble("val_sum")
        ), tenantId, serviceId, metricName, Timestamp.from(startTime), Timestamp.from(endTime));
    }
}
