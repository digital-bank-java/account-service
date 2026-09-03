package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationRequestedEvent(
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
        AccountId sourceAccountId,
        AccountId destinationAccountId,
        BigDecimal amount,
        String currency,
        Instant expiresAt,
        String payloadFingerprint) {}
