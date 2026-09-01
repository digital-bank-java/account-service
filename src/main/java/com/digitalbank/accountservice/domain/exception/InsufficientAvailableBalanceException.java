package com.digitalbank.accountservice.domain.exception;

import java.math.BigDecimal;

import com.digitalbank.accountservice.domain.model.AccountId;

public class InsufficientAvailableBalanceException extends RuntimeException {

	private final AccountId accountId;
	private final BigDecimal requestedAmount;

	public InsufficientAvailableBalanceException(AccountId accountId, BigDecimal requestedAmount) {
		super("Account has insufficient available balance");
		this.accountId = accountId;
		this.requestedAmount = requestedAmount;
	}

	public AccountId accountId() {
		return accountId;
	}

	public BigDecimal requestedAmount() {
		return requestedAmount;
	}
}
