package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID> {

	List<AccountJpaEntity> findByCustomerId(UUID customerId);
}
