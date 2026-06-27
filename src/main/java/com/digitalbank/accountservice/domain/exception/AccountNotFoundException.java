package com.digitalbank.accountservice.domain.exception;

import com.digitalbank.accountservice.domain.model.AccountId;

public class AccountNotFoundException extends RuntimeException {

	private final AccountId accountId;

	public AccountNotFoundException(AccountId accountId) {
		super("Account was not found");
		this.accountId = accountId;
	}

	public AccountId accountId() {
		return accountId;
	}
}
