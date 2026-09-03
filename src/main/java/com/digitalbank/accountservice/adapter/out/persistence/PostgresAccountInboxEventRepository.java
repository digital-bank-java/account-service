package com.digitalbank.accountservice.adapter.out.persistence;

import com.digitalbank.accountservice.application.port.in.InboxEventView;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.out.AccountInboxEventRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class PostgresAccountInboxEventRepository implements AccountInboxEventRepository {

    private final SpringDataAccountInboxEventRepository repository;

    PostgresAccountInboxEventRepository(SpringDataAccountInboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<InboxEventView> findByEventId(String eventId) {
        return repository.findById(eventId).map(AccountInboxEventJpaMapper::toView);
    }

    @Override
    public Optional<InboxEventView> findByLedgerPostingId(String ledgerPostingId) {
        return repository.findByLedgerPostingId(ledgerPostingId).map(AccountInboxEventJpaMapper::toView);
    }

    @Override
    public InboxEventView save(LedgerPostingOutcomeCommand command, Instant processedAt) {
        return AccountInboxEventJpaMapper.toView(
                repository.saveAndFlush(AccountInboxEventJpaMapper.toEntity(command, processedAt)));
    }
}
