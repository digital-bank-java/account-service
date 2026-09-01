package com.digitalbank.accountservice.domain.exception;

public class LedgerPostingOutcomeConflictException extends RuntimeException {

	public LedgerPostingOutcomeConflictException(String message) {
		super(message);
	}
}
