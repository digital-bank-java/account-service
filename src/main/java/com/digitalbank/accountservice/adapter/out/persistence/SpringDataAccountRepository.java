package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface SpringDataAccountRepository extends JpaRepository<AccountJpaEntity, UUID>, JpaSpecificationExecutor<AccountJpaEntity> {

	List<AccountJpaEntity> findByCustomerId(UUID customerId);
}
