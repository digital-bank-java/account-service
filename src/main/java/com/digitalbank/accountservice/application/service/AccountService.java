package com.digitalbank.accountservice.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.digitalbank.accountservice.application.port.in.AccountProfile;
import com.digitalbank.accountservice.application.port.in.GetAccountInputPort;
import com.digitalbank.accountservice.application.port.in.ListCustomerAccountsInputPort;
import com.digitalbank.accountservice.application.port.in.OpenAccountCommand;
import com.digitalbank.accountservice.application.port.in.OpenAccountInputPort;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.domain.exception.AccountNotFoundException;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

@Service
public class AccountService implements OpenAccountInputPort, GetAccountInputPort, ListCustomerAccountsInputPort {

	private final AccountRepository accountRepository;
	private final Clock clock;

	public AccountService(AccountRepository accountRepository, Clock clock) {
		this.accountRepository = accountRepository;
		this.clock = clock;
	}

	public Account openAccount(
			CustomerId customerId,
			String accountNumber,
			String iban,
			AccountType type,
			String currency,
			String openingRequestId) {
		var account = Account.open(
				AccountId.newId(),
				customerId,
				accountNumber,
				iban,
				type,
				currency,
				openingRequestId,
				clock.instant());

		return accountRepository.save(account);
	}

	public Optional<Account> findById(AccountId accountId) {
		return accountRepository.findById(accountId);
	}

	public List<Account> findByCustomerId(CustomerId customerId) {
		return accountRepository.findByCustomerId(customerId);
	}

	@Override
	public AccountProfile openAccount(OpenAccountCommand command) {
		var account = openAccount(
				command.customerId(),
				command.accountNumber(),
				command.iban(),
				command.accountType(),
				command.currency(),
				command.openingRequestId());
		return AccountProfile.fromAccount(account);
	}

	@Override
	public AccountProfile getAccount(AccountId accountId) {
		return accountRepository.findById(accountId)
				.map(AccountProfile::fromAccount)
				.orElseThrow(() -> new AccountNotFoundException(accountId));
	}

	@Override
	public List<AccountProfile> listCustomerAccounts(CustomerId customerId) {
		return accountRepository.findByCustomerId(customerId).stream()
				.map(AccountProfile::fromAccount)
				.toList();
	}
}
