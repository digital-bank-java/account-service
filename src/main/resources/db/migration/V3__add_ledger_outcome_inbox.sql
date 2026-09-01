alter table account_reservations
    add column ledger_posting_id varchar(100),
    add column reversed_by_ledger_posting_id varchar(100);

alter table account_reservations
    drop constraint ck_account_reservations_status;

alter table account_reservations
    add constraint uq_account_reservations_ledger_posting_id unique (ledger_posting_id),
    add constraint uq_account_reservations_reversed_by_posting_id unique (reversed_by_ledger_posting_id),
    add constraint ck_account_reservations_status check (status in ('ACTIVE', 'COMMITTED', 'RELEASED', 'REVERSED')),
    add constraint ck_account_reservations_posting_metadata check (
        (status = 'ACTIVE' and ledger_posting_id is null and reversed_by_ledger_posting_id is null)
        or (status in ('COMMITTED', 'RELEASED') and ledger_posting_id is not null and reversed_by_ledger_posting_id is null)
        or (status = 'REVERSED' and ledger_posting_id is not null and reversed_by_ledger_posting_id is not null)
    );

create table account_inbox_events (
    event_id varchar(100) not null,
    ledger_posting_id varchar(100) not null,
    reservation_request_id varchar(100) not null,
    outcome varchar(30) not null,
    original_posting_id varchar(100),
    processed_at timestamp with time zone not null default now(),
    constraint pk_account_inbox_events primary key (event_id),
    constraint uq_account_inbox_events_ledger_posting_id unique (ledger_posting_id),
    constraint fk_account_inbox_events_reservation_request foreign key (reservation_request_id)
        references account_reservations (reservation_request_id),
    constraint ck_account_inbox_events_event_id_non_blank check (length(btrim(event_id)) > 0),
    constraint ck_account_inbox_events_ledger_posting_id_non_blank check (length(btrim(ledger_posting_id)) > 0),
    constraint ck_account_inbox_events_reservation_request_id_non_blank check (length(btrim(reservation_request_id)) > 0),
    constraint ck_account_inbox_events_outcome check (outcome in ('COMPLETED', 'FAILED', 'REVERSED')),
    constraint ck_account_inbox_events_original_posting check (
        (outcome = 'REVERSED' and original_posting_id is not null)
        or (outcome in ('COMPLETED', 'FAILED') and original_posting_id is null)
    ),
    constraint ck_account_inbox_events_processed_at_present check (processed_at is not null)
);

create index idx_account_inbox_events_reservation_request_id
    on account_inbox_events (reservation_request_id);
