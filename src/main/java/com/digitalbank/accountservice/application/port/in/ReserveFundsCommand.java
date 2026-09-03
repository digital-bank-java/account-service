package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record ReserveFundsCommand(
        String reservationRequestId,
        AccountId accountId,
        String currency,
        BigDecimal amount,
        String correlationId,
        String causationId,
        Instant expiresAt) {

    public ReserveFundsCommand {
        reservationRequestId = requireText(reservationRequestId, "Reservation request id is required");
        Objects.requireNonNull(accountId, "Account id is required");
        currency = requireCurrency(currency);
        amount = requirePositiveAmount(amount);
        correlationId = requireText(correlationId, "Correlation id is required");
        causationId = requireText(causationId, "Causation id is required");
        Objects.requireNonNull(expiresAt, "Reservation expiry timestamp is required");
        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String requireCurrency(String value) {
        var currency = requireText(value, "Currency is required");
        if (currency.length() != 3 || !currency.equals(currency.toUpperCase())) {
            throw new IllegalArgumentException("Currency must be a 3-letter uppercase ISO currency code");
        }
        return currency;
    }

    private static BigDecimal requirePositiveAmount(BigDecimal value) {
        Objects.requireNonNull(value, "Reservation amount is required");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("Reservation amount must be positive");
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Reservation amount must have no more than 4 decimal places", exception);
        }
    }
}
