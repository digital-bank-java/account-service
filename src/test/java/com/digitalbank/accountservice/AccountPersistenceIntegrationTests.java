package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.service.AccountService;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

@SpringBootTest
@Testcontainers
class AccountPersistenceIntegrationTests {

	private static final Instant FIXED_NOW = Instant.parse("2026-01-01T10:15:30Z");

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	void opensAndLoadsAccount() {
		var customerId = CustomerId.newId();

		var account = accountService.openAccount(
				customerId,
				"100000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-account-request-001");

		var savedAccount = accountService.findById(account.id());

		assertThat(savedAccount).hasValueSatisfying(saved -> {
			assertThat(saved.id()).isEqualTo(account.id());
			assertThat(saved.customerId()).isEqualTo(customerId);
			assertThat(saved.accountNumber()).isEqualTo("100000000001");
			assertThat(saved.iban()).isEqualTo("AE070331234567890123456");
			assertThat(saved.type()).isEqualTo(AccountType.CURRENT);
			assertThat(saved.currency()).isEqualTo("AED");
			assertThat(saved.status()).isEqualTo(AccountStatus.ACTIVE);
			assertThat(saved.currentBalance()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(saved.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
			assertThat(saved.openingRequestId()).isEqualTo("open-account-request-001");
			assertThat(saved.version()).isZero();
			assertThat(saved.createdAt()).isEqualTo(FIXED_NOW);
			assertThat(saved.updatedAt()).isEqualTo(FIXED_NOW);
		});
	}

	@Test
	void findsAccountsByCustomerId() {
		var now = Instant.parse("2026-01-02T10:15:30Z");
		var customerId = CustomerId.newId();
		var currentAccount = Account.open(
				AccountId.newId(),
				customerId,
				"100000000002",
				"AE070331234567890123457",
				AccountType.CURRENT,
				"AED",
				"open-account-request-002",
				now);
		var savingsAccount = Account.open(
				AccountId.newId(),
				customerId,
				"100000000003",
				"AE070331234567890123458",
				AccountType.SAVINGS,
				"AED",
				"open-account-request-003",
				now);

		accountRepository.save(currentAccount);
		accountRepository.save(savingsAccount);

		var accounts = accountRepository.findByCustomerId(customerId);

		assertThat(accounts)
				.extracting(Account::accountNumber)
				.containsExactlyInAnyOrder("100000000002", "100000000003");
	}

	@TestConfiguration
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
		}
	}
}
