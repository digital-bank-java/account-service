package com.digitalbank.accountservice.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccountTests {

	private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");

	@Test
	void opensActiveAccountWithZeroBalances() {
		var accountId = AccountId.newId();
		var customerId = CustomerId.newId();

		var account = Account.open(
				accountId,
				customerId,
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-request-001",
				NOW);

		assertThat(account.id()).isEqualTo(accountId);
		assertThat(account.customerId()).isEqualTo(customerId);
		assertThat(account.accountNumber()).isEqualTo("1000000001");
		assertThat(account.iban()).isEqualTo("AE070331234567890123456");
		assertThat(account.type()).isEqualTo(AccountType.CURRENT);
		assertThat(account.currency()).isEqualTo("AED");
		assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(account.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(account.openingRequestId()).isEqualTo("open-request-001");
		assertThat(account.version()).isZero();
		assertThat(account.createdAt()).isEqualTo(NOW);
		assertThat(account.updatedAt()).isEqualTo(NOW);
		assertThat(account.closedAt()).isNull();
	}

	@Test
	void rejectsMissingCustomerId() {
		assertThatThrownBy(() -> Account.open(
				AccountId.newId(),
				null,
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-request-001",
				NOW))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("Customer id is required");
	}

	@Test
	void rejectsBlankAccountNumber() {
		assertThatThrownBy(() -> Account.open(
				AccountId.newId(),
				CustomerId.newId(),
				" ",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-request-001",
				NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Account number is required");
	}

	@Test
	void rejectsLowercaseCurrency() {
		assertThatThrownBy(() -> Account.open(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"aed",
				"open-request-001",
				NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Currency must be a 3-letter uppercase ISO currency code");
	}

	@Test
	void rejectsInvalidCurrencyLength() {
		assertThatThrownBy(() -> Account.open(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AEDX",
				"open-request-001",
				NOW))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Currency must be a 3-letter uppercase ISO currency code");
	}

	@Test
	void rejectsNegativeBalances() {
		assertThatThrownBy(() -> new Account(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				AccountStatus.ACTIVE,
				BigDecimal.valueOf(-1),
				BigDecimal.ZERO,
				"open-request-001",
				0L,
				NOW,
				NOW,
				null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Current balance must not be negative");
	}

	@Test
	void rejectsUpdatedTimestampBeforeCreatedTimestamp() {
		assertThatThrownBy(() -> new Account(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				AccountStatus.ACTIVE,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"open-request-001",
				0L,
				NOW,
				NOW.minusSeconds(1),
				null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Account update timestamp must not be before creation timestamp");
	}

	@Test
	void rejectsClosedAccountWithoutClosedTimestamp() {
		assertThatThrownBy(() -> new Account(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				AccountStatus.CLOSED,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"open-request-001",
				1L,
				NOW,
				NOW,
				null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Closed account requires close timestamp");
	}

	@Test
	void rejectsActiveAccountWithClosedTimestamp() {
		assertThatThrownBy(() -> new Account(
				AccountId.newId(),
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				AccountStatus.ACTIVE,
				BigDecimal.ZERO,
				BigDecimal.ZERO,
				"open-request-001",
				1L,
				NOW,
				NOW,
				NOW.plusSeconds(1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Only closed accounts may have close timestamp");
	}
}
