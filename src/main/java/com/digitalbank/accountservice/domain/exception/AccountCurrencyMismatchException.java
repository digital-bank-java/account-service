package com.digitalbank.accountservice.domain.exception;

import com.digitalbank.accountservice.domain.model.AccountId;

public class AccountCurrencyMismatchException extends RuntimeException {

	private final AccountId accountId;
	private final String currency;

	public AccountCurrencyMismatchException(AccountId accountId, String currency) {
		super("Reservation currency does not match account currency");
		this.accountId = accountId;
		this.currency = currency;
	}

	public AccountId accountId() {
		return accountId;
	}

	public String currency() {
		return currency;
	}
}
