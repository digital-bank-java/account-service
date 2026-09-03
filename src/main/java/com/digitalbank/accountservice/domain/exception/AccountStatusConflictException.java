package com.digitalbank.accountservice.domain.exception;

import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;

public class AccountStatusConflictException extends RuntimeException {

    public AccountStatusConflictException(AccountId accountId, AccountStatus status, String operation) {
        super("Account " + accountId + " cannot " + operation + " in status " + status);
    }
}
