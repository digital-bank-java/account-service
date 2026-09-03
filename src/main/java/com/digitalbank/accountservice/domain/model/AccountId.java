package com.digitalbank.accountservice.domain.model;

import java.util.UUID;

public record AccountId(UUID value) {

    public AccountId {
        if (value == null) {
            throw new IllegalArgumentException("Account id is required");
        }
    }

    public static AccountId newId() {
        return new AccountId(UUID.randomUUID());
    }
}
