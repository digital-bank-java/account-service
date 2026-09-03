package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "account_number", nullable = false, unique = true, length = 34)
    private String accountNumber;

    @Column(name = "iban", unique = true, length = 34)
    private String iban;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentBalance;

    @Column(name = "available_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "opening_request_id", unique = true, length = 100)
    private String openingRequestId;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected AccountJpaEntity() {}

    AccountJpaEntity(
            UUID id,
            UUID customerId,
            String accountNumber,
            String iban,
            AccountType accountType,
            String currency,
            AccountStatus status,
            BigDecimal currentBalance,
            BigDecimal availableBalance,
            String openingRequestId,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Instant closedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.iban = iban;
        this.accountType = accountType;
        this.currency = currency;
        this.status = status;
        this.currentBalance = currentBalance;
        this.availableBalance = availableBalance;
        this.openingRequestId = openingRequestId;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.closedAt = closedAt;
    }

    UUID id() {
        return id;
    }

    UUID customerId() {
        return customerId;
    }

    String accountNumber() {
        return accountNumber;
    }

    String iban() {
        return iban;
    }

    AccountType accountType() {
        return accountType;
    }

    String currency() {
        return currency;
    }

    AccountStatus status() {
        return status;
    }

    BigDecimal currentBalance() {
        return currentBalance;
    }

    BigDecimal availableBalance() {
        return availableBalance;
    }

    String openingRequestId() {
        return openingRequestId;
    }

    long version() {
        return version;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    Instant closedAt() {
        return closedAt;
    }
}
