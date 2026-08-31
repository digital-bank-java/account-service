package com.digitalbank.accountservice.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.digitalbank.accountservice.domain.exception.AccountStatusConflictException;

public record Account(
		AccountId id,
		CustomerId customerId,
		String accountNumber,
		String iban,
		AccountType type,
		String currency,
		AccountStatus status,
		BigDecimal currentBalance,
		BigDecimal availableBalance,
		String openingRequestId,
		long version,
		Instant createdAt,
		Instant updatedAt,
		Instant closedAt) {

	private static final BigDecimal ZERO_BALANCE = BigDecimal.ZERO.setScale(4);

	public Account {
		Objects.requireNonNull(id, "Account id is required");
		Objects.requireNonNull(customerId, "Customer id is required");
		accountNumber = requireText(accountNumber, "Account number is required");
		Objects.requireNonNull(type, "Account type is required");
		currency = requireCurrency(currency);
		Objects.requireNonNull(status, "Account status is required");
		currentBalance = requireNonNegative(currentBalance, "Current balance is required");
		availableBalance = requireNonNegative(availableBalance, "Available balance is required");
		Objects.requireNonNull(createdAt, "Account creation timestamp is required");
		Objects.requireNonNull(updatedAt, "Account update timestamp is required");
		if (version < 0) {
			throw new IllegalArgumentException("Account version must not be negative");
		}
		if (updatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("Account update timestamp must not be before creation timestamp");
		}
		if (closedAt != null && closedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException("Account close timestamp must not be before creation timestamp");
		}
		if (status == AccountStatus.CLOSED && closedAt == null) {
			throw new IllegalArgumentException("Closed account requires close timestamp");
		}
		if (status != AccountStatus.CLOSED && closedAt != null) {
			throw new IllegalArgumentException("Only closed accounts may have close timestamp");
		}
	}

	public static Account open(
			AccountId id,
			CustomerId customerId,
			String accountNumber,
			String iban,
			AccountType type,
			String currency,
			String openingRequestId,
			Instant now) {
		return new Account(
				id,
				customerId,
				accountNumber,
				iban,
				type,
				currency,
				AccountStatus.ACTIVE,
				ZERO_BALANCE,
				ZERO_BALANCE,
				openingRequestId,
				0L,
				now,
				now,
				null);
	}

	public void requireActiveForMonetaryOperation(String operation) {
		if (status != AccountStatus.ACTIVE) {
			throw new AccountStatusConflictException(id, status, operation);
		}
	}

	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}
		return value;
	}

	private static String requireCurrency(String value) {
		var currency = requireText(value, "Currency is required");
		if (currency.length() != 3 || !currency.equals(currency.toUpperCase())) {
			throw new IllegalArgumentException("Currency must be a 3-letter uppercase ISO currency code");
		}
		return currency;
	}

	private static BigDecimal requireNonNegative(BigDecimal value, String message) {
		Objects.requireNonNull(value, message);
		if (value.signum() < 0) {
			throw new IllegalArgumentException(message.replace("is required", "must not be negative"));
		}
		return value;
	}
}
