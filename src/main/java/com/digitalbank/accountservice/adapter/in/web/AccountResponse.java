package com.digitalbank.accountservice.adapter.in.web;

import com.digitalbank.accountservice.application.port.in.AccountProfile;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import java.math.BigDecimal;
import java.time.Instant;

record AccountResponse(
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

    static AccountResponse from(AccountProfile profile) {
        return new AccountResponse(
                profile.accountId(),
                profile.customerId(),
                profile.accountNumber(),
                profile.iban(),
                profile.accountType(),
                profile.currency(),
                profile.status(),
                profile.currentBalance(),
                profile.availableBalance(),
                profile.version(),
                profile.createdAt(),
                profile.updatedAt());
    }
}
