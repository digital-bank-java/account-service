package com.digitalbank.accountservice.adapter.out.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataAccountInboxEventRepository extends JpaRepository<AccountInboxEventJpaEntity, String> {

    Optional<AccountInboxEventJpaEntity> findByLedgerPostingId(String ledgerPostingId);
}
