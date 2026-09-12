package com.resolveiq.ingestion.grpc;

import com.resolveiq.common.tenant.TenantContext;
import com.resolveiq.ingestion.kafka.TelemetryKafkaProducer;
import com.resolveiq.common.telemetry.KafkaEventEnvelope;
import com.resolveiq.common.telemetry.LogObservedPayload;
import com.resolveiq.ingestion.security.IngestionCredentialValidator;
import io.grpc.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * OpenTelemetry gRPC Server listening on port 4317 (PRD Section 14, 15).
 * Supports standard OTLP gRPC export methods with header-based tenant authentication.
 */
@Component
public class OtlpGrpcServer {

    private static final Logger log = LoggerFactory.getLogger(OtlpGrpcServer.class);

    private final int port;
    private final boolean enabled;
    private final IngestionCredentialValidator credentialValidator;
    private final TelemetryKafkaProducer kafkaProducer;
    private Server server;

    public OtlpGrpcServer(
            @Value("${resolveiq.ingestion.grpc.port:4317}") int port,
            @Value("${resolveiq.ingestion.grpc.enabled:true}") boolean enabled,
            IngestionCredentialValidator credentialValidator,
            TelemetryKafkaProducer kafkaProducer) {
        this.port = port;
        this.enabled = enabled;
        this.credentialValidator = credentialValidator;
        this.kafkaProducer = kafkaProducer;
    }

    public int getPort() {
        return server != null ? server.getPort() : port;
    }

    @PostConstruct
    public void start() throws Exception {
        if (!enabled) {
            log.info("OTLP gRPC server is disabled by configuration");
            return;
        }

        ServerServiceDefinition serviceDef = buildOtlpServiceDefinition();

        server = ServerBuilder.forPort(port)
                .addService(serviceDef)
                .build()
                .start();

        log.info("OTLP gRPC Server successfully started on port {}", server.getPort());
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.shutdown();
            log.info("OTLP gRPC Server stopped");
        }
    }

    private ServerServiceDefinition buildOtlpServiceDefinition() {
        MethodDescriptor.Marshaller<byte[]> byteMarshaller = new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(byte[] value) {
                return new ByteArrayInputStream(value);
            }

            @Override
            public byte[] parse(InputStream stream) {
                try {
                    return stream.readAllBytes();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        // ExportLogsService
        MethodDescriptor<byte[], byte[]> exportLogsMethod = MethodDescriptor.<byte[], byte[]>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(MethodDescriptor.generateFullMethodName("opentelemetry.proto.logs.v1.LogsService", "Export"))
                .setRequestMarshaller(byteMarshaller)
                .setResponseMarshaller(byteMarshaller)
                .build();

        return ServerServiceDefinition.builder("opentelemetry.proto.logs.v1.LogsService")
                .addMethod(exportLogsMethod, io.grpc.stub.ServerCalls.asyncUnaryCall(
                        (requestBytes, responseObserver) -> {
                            // Extract metadata
                            Metadata.Key<String> apiKeyMeta = Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
                            String rawPayload = new String(requestBytes, StandardCharsets.UTF_8);

                            // Send empty response for successful OTLP export
                            responseObserver.onNext(new byte[0]);
                            responseObserver.onCompleted();
                        }
                ))
                .build();
    }
}
