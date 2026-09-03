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
        Instant updatedAt,
        String ledgerPostingId,
        String reversedByLedgerPostingId,
        AccountId destinationAccountId,
        UUID transactionId,
        UUID acceptedEventId) {

    public ReservationView(
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
            Instant updatedAt) {
        this(
                reservationId,
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
                null,
                null,
                null,
                null);
    }

    public ReservationView(
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
            Instant updatedAt,
            String ledgerPostingId,
            String reversedByLedgerPostingId) {
        this(
                reservationId,
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
}
