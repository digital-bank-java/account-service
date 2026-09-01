package com.digitalbank.accountservice.domain.exception;

public class InvalidLedgerPostingEventException extends IllegalArgumentException {

	public InvalidLedgerPostingEventException(String message) {
		super(message);
	}
}
