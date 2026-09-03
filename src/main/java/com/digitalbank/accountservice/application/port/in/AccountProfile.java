package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountProfile(
        String accountId,
        String customerId,
        String accountNumber,
        String iban,
        AccountType accountType,
        String currency,
        AccountStatus status,
        BigDecimal currentBalance,
        BigDecimal availableBalance,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static AccountProfile fromAccount(Account account) {
        return new AccountProfile(
                account.id().value().toString(),
                account.customerId().value().toString(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.currency(),
                account.status(),
                account.currentBalance(),
                account.availableBalance(),
                account.version(),
                account.createdAt(),
                account.updatedAt());
    }
}
