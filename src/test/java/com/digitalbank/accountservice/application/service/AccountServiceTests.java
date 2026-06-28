package com.digitalbank.accountservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.in.ListAccountsQuery;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

class AccountServiceTests {

	private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
	private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:15:30Z"), ZoneOffset.UTC);
	private final AccountService accountService = new AccountService(accountRepository, clock);

	@Test
	void opensAccountForCustomer() {
		var customerId = CustomerId.newId();

		var account = accountService.openAccount(
				customerId,
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-request-001");

		assertThat(account.id()).isNotNull();
		assertThat(account.customerId()).isEqualTo(customerId);
		assertThat(account.type()).isEqualTo(AccountType.CURRENT);
		assertThat(account.currency()).isEqualTo("AED");
		assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(account.createdAt()).isEqualTo(clock.instant());
		assertThat(account.updatedAt()).isEqualTo(clock.instant());
		assertThat(accountRepository.savedAccounts()).containsExactly(account);
	}

	@Test
	void supportsMultipleAccountsForSameCustomerTypeAndCurrency() {
		var customerId = CustomerId.newId();

		var firstAccount = accountService.openAccount(
				customerId,
				"1000000001",
				"AE070331234567890123456",
				AccountType.CURRENT,
				"AED",
				"open-request-001");
		var secondAccount = accountService.openAccount(
				customerId,
				"1000000002",
				"AE070331234567890123457",
				AccountType.CURRENT,
				"AED",
				"open-request-002");

		assertThat(firstAccount.id()).isNotEqualTo(secondAccount.id());
		assertThat(accountService.findByCustomerId(customerId)).containsExactly(firstAccount, secondAccount);
	}

	@Test
	void findsAccountById() {
		var account = accountService.openAccount(
				CustomerId.newId(),
				"1000000001",
				"AE070331234567890123456",
				AccountType.SAVINGS,
				"USD",
				"open-request-001");

		assertThat(accountService.findById(account.id())).contains(account);
	}

	@Test
	void returnsEmptyWhenAccountDoesNotExist() {
		assertThat(accountService.findById(AccountId.newId())).isEmpty();
	}

	@Test
	void listsAccountsForAdministration() {
		var customerId = CustomerId.newId();
		var account = accountService.openAccount(
				customerId,
				"1000000001",
				"AE070331234567890123456",
				AccountType.SAVINGS,
				"USD",
				"open-request-001");

		var page = accountService.listAccounts(new ListAccountsQuery(
				customerId,
				AccountStatus.ACTIVE,
				AccountType.SAVINGS,
				"USD",
				0,
				20,
				List.of()));

		assertThat(page.items()).singleElement().satisfies(profile -> {
			assertThat(profile.accountId()).isEqualTo(account.id().value().toString());
			assertThat(profile.customerId()).isEqualTo(customerId.value().toString());
		});
		assertThat(page.pageNumber()).isZero();
		assertThat(page.pageSize()).isEqualTo(20);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.totalPages()).isEqualTo(1);
		assertThat(page.last()).isTrue();
	}

	private static final class InMemoryAccountRepository implements AccountRepository {

		private final List<Account> accounts = new ArrayList<>();

		@Override
		public Account save(Account account) {
			accounts.add(account);
			return account;
		}

		@Override
		public Optional<Account> findById(AccountId accountId) {
			return accounts.stream()
					.filter(account -> account.id().equals(accountId))
					.findFirst();
		}

		@Override
		public List<Account> findByCustomerId(CustomerId customerId) {
			return accounts.stream()
					.filter(account -> account.customerId().equals(customerId))
					.toList();
		}

		@Override
		public AccountSearchResult search(AccountSearchCriteria criteria) {
			var matches = accounts.stream()
					.filter(account -> criteria.customerId() == null || account.customerId().equals(criteria.customerId()))
					.filter(account -> criteria.status() == null || account.status().equals(criteria.status()))
					.filter(account -> criteria.accountType() == null || account.type().equals(criteria.accountType()))
					.filter(account -> criteria.currency() == null || account.currency().equals(criteria.currency()))
					.toList();
			return new AccountSearchResult(
					matches,
					criteria.pageNumber(),
					criteria.pageSize(),
					matches.size(),
					matches.isEmpty() ? 0 : 1,
					true);
		}

		private List<Account> savedAccounts() {
			return List.copyOf(accounts);
		}
	}
}
