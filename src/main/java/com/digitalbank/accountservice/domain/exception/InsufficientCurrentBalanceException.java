package com.digitalbank.accountservice.domain.exception;

import com.digitalbank.accountservice.domain.model.AccountId;
import java.math.BigDecimal;

public class InsufficientCurrentBalanceException extends RuntimeException {

    public InsufficientCurrentBalanceException(AccountId accountId, BigDecimal amount) {
        super("Current balance is insufficient for account " + accountId.value() + " and amount " + amount);
    }
}
