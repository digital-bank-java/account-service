package com.digitalbank.accountservice.application.service;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

@Service
public class AccountService {

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
}
