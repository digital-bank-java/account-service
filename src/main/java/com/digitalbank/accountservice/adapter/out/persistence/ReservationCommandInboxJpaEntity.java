package com.digitalbank.accountservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_reservation_inbox_events")
class ReservationCommandInboxJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "payload_fingerprint", nullable = false, length = 64)
    private String payloadFingerprint;

    @Column(name = "reservation_request_id", nullable = false, length = 100)
    private String reservationRequestId;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "causation_id", nullable = false, length = 100)
    private String causationId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ReservationCommandInboxJpaEntity() {}

    ReservationCommandInboxJpaEntity(
            UUID eventId,
            String eventType,
            String payloadFingerprint,
            String reservationRequestId,
            UUID transactionId,
            String correlationId,
            String causationId,
            UUID reservationId,
            Instant processedAt) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payloadFingerprint = payloadFingerprint;
        this.reservationRequestId = reservationRequestId;
        this.transactionId = transactionId;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.reservationId = reservationId;
        this.processedAt = processedAt;
    }

    UUID eventId() {
        return eventId;
    }

    String eventType() {
        return eventType;
    }

    String payloadFingerprint() {
        return payloadFingerprint;
    }

    String reservationRequestId() {
        return reservationRequestId;
    }

    UUID transactionId() {
        return transactionId;
    }

    String correlationId() {
        return correlationId;
    }

    String causationId() {
        return causationId;
    }

    UUID reservationId() {
        return reservationId;
    }

    Instant processedAt() {
        return processedAt;
    }
}
