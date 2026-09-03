package com.digitalbank.accountservice.application.port.in;

public interface LedgerPostingOutcomeInputPort {

	LedgerPostingOutcomeResult handle(LedgerPostingOutcomeCommand command);
}
