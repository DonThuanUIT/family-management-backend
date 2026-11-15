package com.familyfund.contribution.internal.repository;

import com.familyfund.contribution.internal.entity.PaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, String> {

    boolean existsByReferenceCode(String referenceCode);

    Optional<PaymentWebhook> findByReferenceCode(String referenceCode);
}
