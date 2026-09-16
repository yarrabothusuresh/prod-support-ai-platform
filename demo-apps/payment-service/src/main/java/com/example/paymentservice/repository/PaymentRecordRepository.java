package com.example.paymentservice.repository;

import com.example.paymentservice.model.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
    Optional<PaymentRecord> findByPaymentId(String paymentId);
}
