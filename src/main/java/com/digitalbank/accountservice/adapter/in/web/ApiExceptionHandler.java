package com.digitalbank.accountservice.adapter.in.web;

import com.digitalbank.accountservice.domain.exception.AccountNotFoundException;
import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;
import com.digitalbank.accountservice.domain.exception.LedgerPostingOutcomeConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationExpiredException;
import com.digitalbank.accountservice.domain.exception.ReservationRequestConflictException;
import com.digitalbank.accountservice.domain.exception.ReservationStateConflictException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ResponseEntity<ProblemDetail> handleAccountNotFound(AccountNotFoundException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Account not found");
        problem.setType(URI.create("https://digital-bank-java.local/problems/account-not-found"));
        problem.setProperty("accountId", exception.accountId().value());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleAccountConflict(DataIntegrityViolationException exception) {
        var problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Account request conflicts with existing data");
        problem.setTitle("Account conflict");
        problem.setType(URI.create("https://digital-bank-java.local/problems/account-conflict"));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler({
        AccountStatusConflictException.class,
        ReservationExpiredException.class,
        ReservationStateConflictException.class,
        LedgerPostingOutcomeConflictException.class,
        ReservationRequestConflictException.class
    })
    ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        var problemName = problemName(exception);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problem.setTitle(problemName.title());
        problem.setType(URI.create("https://digital-bank-java.local/problems/" + problemName.slug()));
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleValidationFailure(MethodArgumentNotValidException exception) {
        var errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", String.valueOf(error.getDefaultMessage())))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleQueryParameterValidationFailure(ConstraintViolationException exception) {
        var errors = exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList();

        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> handleResponseStatus(ResponseStatusException exception) {
        var problem = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        problem.setTitle("Invalid request");
        problem.setType(URI.create("https://digital-bank-java.local/problems/validation-error"));
        return ResponseEntity.status(exception.getStatusCode()).body(problem);
    }

    private static ProblemName problemName(RuntimeException exception) {
        if (exception instanceof AccountStatusConflictException) {
            return new ProblemName("account-status-conflict", "Account status conflict");
        }
        if (exception instanceof ReservationExpiredException) {
            return new ProblemName("reservation-expired", "Reservation expired");
        }
        if (exception instanceof ReservationStateConflictException) {
            return new ProblemName("reservation-state-conflict", "Reservation state conflict");
        }
        if (exception instanceof LedgerPostingOutcomeConflictException) {
            return new ProblemName("ledger-posting-outcome-conflict", "Ledger posting outcome conflict");
        }
        return new ProblemName("reservation-request-conflict", "Reservation request conflict");
    }

    private record ProblemName(String slug, String title) {}
}
