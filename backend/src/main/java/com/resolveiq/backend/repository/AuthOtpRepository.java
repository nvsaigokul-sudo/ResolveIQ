package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.AuthOtpEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AuthOtpRepository extends JpaRepository<AuthOtpEntity, UUID> {

    Optional<AuthOtpEntity> findTopByEmailIgnoreCaseAndConsumedFalseOrderByCreatedAtDesc(String email);

    long countByEmailIgnoreCaseAndCreatedAtAfter(String email, Instant after);

    Optional<AuthOtpEntity> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
}
