alter table account_reservations
    drop constraint ck_account_reservations_status,
    drop constraint ck_account_reservations_posting_metadata;

alter table account_reservations
    add constraint ck_account_reservations_status
        check (status in ('ACTIVE', 'EXPIRED', 'COMMITTED', 'RELEASED', 'REVERSED')),
    add constraint ck_account_reservations_posting_metadata check (
        (status in ('ACTIVE', 'EXPIRED') and ledger_posting_id is null and reversed_by_ledger_posting_id is null)
        or (status in ('COMMITTED', 'RELEASED') and ledger_posting_id is not null and reversed_by_ledger_posting_id is null)
        or (status = 'REVERSED' and ledger_posting_id is not null and reversed_by_ledger_posting_id is not null)
    );

create index idx_account_reservations_active_expiry
    on account_reservations (expires_at, id)
    where status = 'ACTIVE';
