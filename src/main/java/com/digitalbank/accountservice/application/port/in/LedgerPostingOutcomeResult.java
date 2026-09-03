package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.ReservationStatus;

public record LedgerPostingOutcomeResult(
        String eventId, String reservationRequestId, ReservationStatus reservationStatus, boolean duplicate) {}
