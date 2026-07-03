package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.CustomerId;

final class AccountJpaMapper {

    private AccountJpaMapper() {}

    static AccountJpaEntity toEntity(Account account) {
        return new AccountJpaEntity(
                account.id().value(),
                account.customerId().value(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.currency(),
                account.status(),
                account.currentBalance(),
                account.availableBalance(),
                account.openingRequestId(),
                account.version(),
                account.createdAt(),
                account.updatedAt(),
                account.closedAt());
    }

    static Account toDomain(AccountJpaEntity entity) {
        return new Account(
                new AccountId(entity.id()),
                new CustomerId(entity.customerId()),
                entity.accountNumber(),
                entity.iban(),
                entity.accountType(),
                entity.currency(),
                entity.status(),
                entity.currentBalance(),
                entity.availableBalance(),
                entity.openingRequestId(),
                entity.version(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.closedAt());
    }
}
