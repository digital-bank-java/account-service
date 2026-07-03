package com.digitalbank.accountservice.application.port.in;

public interface OpenAccountInputPort {

    AccountProfile openAccount(OpenAccountCommand command);
}
