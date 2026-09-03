package com.digitalbank.accountservice.domain.exception;

import java.math.BigDecimal;

import com.digitalbank.accountservice.domain.model.AccountId;

public class InsufficientCurrentBalanceException extends RuntimeException {

	public InsufficientCurrentBalanceException(AccountId accountId, BigDecimal amount) {
		super("Current balance is insufficient for account " + accountId.value() + " and amount " + amount);
	}
}
