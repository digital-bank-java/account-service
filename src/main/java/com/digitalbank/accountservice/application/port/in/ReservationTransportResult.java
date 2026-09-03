package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.util.UUID;

public record ReservationTransportResult(UUID eventId, ReservationStatus status, boolean duplicate) {

    public static ReservationTransportResult accepted(UUID eventId, boolean duplicate) {
        return new ReservationTransportResult(eventId, ReservationStatus.ACTIVE, duplicate);
    }

    public static ReservationTransportResult released(UUID eventId, boolean duplicate) {
        return new ReservationTransportResult(eventId, ReservationStatus.RELEASED, duplicate);
    }

    public static ReservationTransportResult rejected(UUID eventId, boolean duplicate) {
        return new ReservationTransportResult(eventId, null, duplicate);
    }
}
