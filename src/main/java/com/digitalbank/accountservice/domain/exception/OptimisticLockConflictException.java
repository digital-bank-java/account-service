package com.digitalbank.accountservice.domain.exception;

public class OptimisticLockConflictException extends RuntimeException {

	public OptimisticLockConflictException() {
		super("Account was changed by another request");
	}

	public OptimisticLockConflictException(Throwable cause) {
		super("Account was changed by another request", cause);
	}
}
