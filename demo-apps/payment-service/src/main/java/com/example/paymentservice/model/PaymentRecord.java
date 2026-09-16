package com.example.paymentservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public PaymentRecord() {}

    public PaymentRecord(String paymentId, BigDecimal amount, String currency, String status) {
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
