package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.CustomerId;

@Repository
class PostgresAccountRepository implements AccountRepository {

	private final SpringDataAccountRepository repository;

	PostgresAccountRepository(SpringDataAccountRepository repository) {
		this.repository = repository;
	}

	@Override
	public Account save(Account account) {
		return AccountJpaMapper.toDomain(repository.saveAndFlush(AccountJpaMapper.toEntity(account)));
	}

	@Override
	public Optional<Account> findById(AccountId accountId) {
		return repository.findById(accountId.value()).map(AccountJpaMapper::toDomain);
	}

	@Override
	public List<Account> findByCustomerId(CustomerId customerId) {
		return repository.findByCustomerId(customerId.value()).stream()
				.map(AccountJpaMapper::toDomain)
				.toList();
	}
}
