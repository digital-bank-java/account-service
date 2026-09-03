package com.digitalbank.accountservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationExpiredException;
import com.digitalbank.accountservice.domain.exception.ReservationRequestConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsDomainConflictsToStableConflictProblemDetails() {
        var cases = List.of(
                new Case(
                        new AccountStatusConflictException(
                                new AccountId(UUID.randomUUID()), AccountStatus.SUSPENDED, "reserve funds"),
                        "account-status-conflict",
                        "Account status conflict"),
                new Case(
                        new ReservationExpiredException("reservation-001", Instant.parse("2026-01-01T10:15:30Z")),
                        "reservation-expired",
                        "Reservation expired"),
                new Case(
                        new ReservationStateConflictException("reservation-002", ReservationStatus.COMMITTED),
                        "reservation-state-conflict",
                        "Reservation state conflict"),
                new Case(
                        new LedgerPostingOutcomeConflictException("posting conflict"),
                        "ledger-posting-outcome-conflict",
                        "Ledger posting outcome conflict"),
                new Case(
                        new ReservationRequestConflictException("reservation-003"),
                        "reservation-request-conflict",
                        "Reservation request conflict"));

        for (var testCase : cases) {
            var response = handler.handleConflict(testCase.exception());

            assertThat(response.getStatusCode().value()).isEqualTo(409);
            assertThat(response.getBody()).isNotNull().satisfies(problem -> {
                assertThat(problem.getTitle()).isEqualTo(testCase.title());
                assertThat(problem.getType())
                        .hasToString("https://digital-bank-java.local/problems/" + testCase.type());
                assertThat(problem.getDetail()).isEqualTo(testCase.exception().getMessage());
            });
        }
    }

    private record Case(RuntimeException exception, String type, String title) {}
}
