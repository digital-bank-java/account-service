package com.digitalbank.accountservice.application.port.out;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountReservationEvent(
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
        UUID sourceAccountId,
        UUID destinationAccountId,
        @JsonSerialize(using = ToStringSerializer.class) BigDecimal amount,
        String currency,
        Instant expiresAt,
        String status,
        String rejectionCode,
        String rejectionReason,
        String releaseReason,
        String postingRequestId) {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PRODUCER = "account-service";

    public boolean isAccepted() {
        return "AccountReservationAccepted.v1".equals(eventType);
    }
}
