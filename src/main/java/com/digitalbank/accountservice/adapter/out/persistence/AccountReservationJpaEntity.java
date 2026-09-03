package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.domain.model.ReservationStatus;
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
@Table(name = "account_reservations")
class AccountReservationJpaEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "reservation_request_id", nullable = false, unique = true, length = 100)
    private String reservationRequestId;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 100)
    private String causationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ledger_posting_id", unique = true, length = 100)
    private String ledgerPostingId;

    @Column(name = "reversed_by_ledger_posting_id", unique = true, length = 100)
    private String reversedByLedgerPostingId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "accepted_event_id", unique = true)
    private UUID acceptedEventId;

    protected AccountReservationJpaEntity() {}

    AccountReservationJpaEntity(
            UUID id,
            UUID accountId,
            String reservationRequestId,
            String currency,
            BigDecimal amount,
            String correlationId,
            String causationId,
            ReservationStatus status,
            Instant expiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt) {
        this(
                id,
                accountId,
                reservationRequestId,
                currency,
                amount,
                correlationId,
                causationId,
                status,
                expiresAt,
                version,
                createdAt,
                updatedAt,
                null,
                null);
    }

    AccountReservationJpaEntity(
            UUID id,
            UUID accountId,
            String reservationRequestId,
            String currency,
            BigDecimal amount,
            String correlationId,
            String causationId,
            ReservationStatus status,
            Instant expiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String ledgerPostingId,
            String reversedByLedgerPostingId) {
        this(
                id,
                accountId,
                reservationRequestId,
                currency,
                amount,
                correlationId,
                causationId,
                status,
                expiresAt,
                version,
                createdAt,
                updatedAt,
                ledgerPostingId,
                reversedByLedgerPostingId,
                null,
                null,
                null);
    }

    AccountReservationJpaEntity(
            UUID id,
            UUID accountId,
            String reservationRequestId,
            String currency,
            BigDecimal amount,
            String correlationId,
            String causationId,
            ReservationStatus status,
            Instant expiresAt,
            long version,
            Instant createdAt,
            Instant updatedAt,
            String ledgerPostingId,
            String reversedByLedgerPostingId,
            UUID destinationAccountId,
            UUID transactionId,
            UUID acceptedEventId) {
        this.id = id;
        this.accountId = accountId;
        this.reservationRequestId = reservationRequestId;
        this.currency = currency;
        this.amount = amount;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.ledgerPostingId = ledgerPostingId;
        this.reversedByLedgerPostingId = reversedByLedgerPostingId;
        this.destinationAccountId = destinationAccountId;
        this.transactionId = transactionId;
        this.acceptedEventId = acceptedEventId;
    }

    UUID id() {
        return id;
    }

    UUID accountId() {
        return accountId;
    }

    String reservationRequestId() {
        return reservationRequestId;
    }

    String currency() {
        return currency;
    }

    BigDecimal amount() {
        return amount;
    }

    String correlationId() {
        return correlationId;
    }

    String causationId() {
        return causationId;
    }

    ReservationStatus status() {
        return status;
    }

    Instant expiresAt() {
        return expiresAt;
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

    String ledgerPostingId() {
        return ledgerPostingId;
    }

    String reversedByLedgerPostingId() {
        return reversedByLedgerPostingId;
    }

    UUID destinationAccountId() {
        return destinationAccountId;
    }

    UUID transactionId() {
        return transactionId;
    }

    UUID acceptedEventId() {
        return acceptedEventId;
    }
}
