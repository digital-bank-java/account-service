package com.digitalbank.accountservice.application.port.in;

public interface ReserveFundsInputPort {

    ReservationView reserve(ReserveFundsCommand command);
}
