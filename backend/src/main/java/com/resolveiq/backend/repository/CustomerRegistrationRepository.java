package com.resolveiq.backend.repository;

import com.resolveiq.backend.domain.CustomerRegistrationEntity;
import com.resolveiq.backend.domain.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRegistrationRepository extends JpaRepository<CustomerRegistrationEntity, UUID> {

    Optional<CustomerRegistrationEntity> findByEmailIgnoreCase(String email);

    Optional<CustomerRegistrationEntity> findByVerificationTokenHash(String verificationTokenHash);

    List<CustomerRegistrationEntity> findByStatusOrderByCreatedAtDesc(RegistrationStatus status);

    List<CustomerRegistrationEntity> findAllByOrderByCreatedAtDesc();

    long countByStatus(RegistrationStatus status);
}
