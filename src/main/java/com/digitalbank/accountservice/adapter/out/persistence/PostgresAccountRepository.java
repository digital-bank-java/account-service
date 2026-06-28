package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.digitalbank.accountservice.application.model.AccountSortOrder;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
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

	@Override
	public AccountSearchResult search(AccountSearchCriteria criteria) {
		var pageRequest = PageRequest.of(
				criteria.pageNumber(),
				criteria.pageSize(),
				sortFor(criteria));
		var page = repository.findAll(specificationFor(criteria), pageRequest);
		var accounts = page.getContent().stream()
				.map(AccountJpaMapper::toDomain)
				.toList();

		return new AccountSearchResult(
				accounts,
				page.getNumber(),
				page.getSize(),
				page.getTotalElements(),
				page.getTotalPages(),
				page.isLast());
	}

	private static Sort sortFor(AccountSearchCriteria criteria) {
		if (criteria.sort() == null || criteria.sort().isEmpty()) {
			return Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id"));
		}

		var orders = criteria.sort().stream()
				.map(PostgresAccountRepository::toOrder)
				.toList();
		return Sort.by(orders);
	}

	private static Sort.Order toOrder(AccountSortOrder sortOrder) {
		var direction = switch (sortOrder.direction()) {
			case ASC -> Sort.Direction.ASC;
			case DESC -> Sort.Direction.DESC;
		};
		return new Sort.Order(direction, sortOrder.property());
	}

	private static Specification<AccountJpaEntity> specificationFor(AccountSearchCriteria criteria) {
		return (root, query, builder) -> {
			var predicate = builder.conjunction();
			if (criteria.customerId() != null) {
				predicate = builder.and(predicate, builder.equal(root.get("customerId"), criteria.customerId().value()));
			}
			if (criteria.status() != null) {
				predicate = builder.and(predicate, builder.equal(root.get("status"), criteria.status()));
			}
			if (criteria.accountType() != null) {
				predicate = builder.and(predicate, builder.equal(root.get("accountType"), criteria.accountType()));
			}
			if (criteria.currency() != null) {
				predicate = builder.and(predicate, builder.equal(root.get("currency"), criteria.currency()));
			}
			return predicate;
		};
	}
}
