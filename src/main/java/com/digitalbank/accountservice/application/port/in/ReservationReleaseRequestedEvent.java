package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;
import java.time.Instant;
import java.util.UUID;

public record ReservationReleaseRequestedEvent(
        UUID eventId,
        String eventType,
        String schemaVersion,
        String producer,
        Instant occurredAt,
        String aggregateId,
        String correlationId,
        String causationId,
        UUID transactionId,
        String reservationRequestId,
        UUID reservationId,
        AccountId sourceAccountId,
        String reason,
        String postingRequestId,
        String payloadFingerprint) {}
