package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReservationView(
        UUID reservationId,
        AccountId accountId,
        String reservationRequestId,
        String currency,
        BigDecimal amount,
        String correlationId,
        String causationId,
        ReservationStatus status,
        Instant expiresAt,
        long version,
        Instant createdAt,
        Instant updatedAt) {}
