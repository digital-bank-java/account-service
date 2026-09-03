package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_reservation_event_outbox")
class AccountReservationEventOutboxJpaEntity {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "producer", nullable = false)
    private String producer;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;

    @Column(name = "correlation_id", nullable = false)
    private String correlationId;

    @Column(name = "causation_id", nullable = false)
    private String causationId;

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "reservation_request_id", nullable = false)
    private String reservationRequestId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "source_account_id", nullable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id")
    private UUID destinationAccountId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private java.math.BigDecimal amount;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "status")
    private String status;

    @Column(name = "rejection_code")
    private String rejectionCode;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "release_reason")
    private String releaseReason;

    @Column(name = "posting_request_id")
    private String postingRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_status", nullable = false)
    private AccountReservationOutboxStatus eventStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "processing_token")
    private UUID processingToken;

    @Column(name = "processing_until")
    private Instant processingUntil;

    @Column(name = "json_payload", nullable = false, columnDefinition = "text")
    private String jsonPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountReservationEventOutboxJpaEntity() {}

    AccountReservationEventOutboxJpaEntity(AccountReservationEvent event, String jsonPayload, Instant now) {
        this.eventId = event.eventId();
        this.eventType = event.eventType();
        this.schemaVersion = event.schemaVersion();
        this.producer = event.producer();
        this.occurredAt = event.occurredAt();
        this.aggregateId = event.aggregateId();
        this.correlationId = event.correlationId();
        this.causationId = event.causationId();
        this.transactionId = event.transactionId();
        this.reservationRequestId = event.reservationRequestId();
        this.reservationId = event.reservationId();
        this.sourceAccountId = event.sourceAccountId();
        this.destinationAccountId = event.destinationAccountId();
        this.amount = event.amount();
        this.currency = event.currency();
        this.expiresAt = event.expiresAt();
        this.status = event.status();
        this.rejectionCode = event.rejectionCode();
        this.rejectionReason = event.rejectionReason();
        this.releaseReason = event.releaseReason();
        this.postingRequestId = event.postingRequestId();
        this.eventStatus = AccountReservationOutboxStatus.PENDING;
        this.availableAt = now;
        this.jsonPayload = jsonPayload;
        this.createdAt = now;
    }

    AccountReservationEvent toEvent() {
        return new AccountReservationEvent(
                eventId,
                eventType,
                schemaVersion,
                producer,
                occurredAt,
                aggregateId,
                correlationId,
                causationId,
                transactionId,
                reservationRequestId,
                reservationId,
                sourceAccountId,
                destinationAccountId,
                amount,
                currency,
                expiresAt,
                status,
                rejectionCode,
                rejectionReason,
                releaseReason,
                postingRequestId);
    }

    String jsonPayload() {
        return jsonPayload;
    }
}
