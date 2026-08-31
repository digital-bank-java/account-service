alter table account_reservations
    add column destination_account_id uuid,
    add column transaction_id uuid,
    add column accepted_event_id uuid;

alter table account_reservations
    drop constraint ck_account_reservations_posting_metadata;

alter table account_reservations
    add constraint uq_account_reservations_accepted_event_id unique (accepted_event_id),
    add constraint ck_account_reservations_posting_metadata check (
        (status in ('ACTIVE', 'EXPIRED') and ledger_posting_id is null and reversed_by_ledger_posting_id is null)
        or (status = 'COMMITTED' and ledger_posting_id is not null and reversed_by_ledger_posting_id is null)
        or (status = 'RELEASED' and reversed_by_ledger_posting_id is null)
        or (status = 'REVERSED' and ledger_posting_id is not null and reversed_by_ledger_posting_id is not null)
    );

create table account_reservation_inbox_events (
    event_id uuid not null,
    event_type varchar(100) not null,
    payload_fingerprint varchar(64) not null,
    reservation_request_id varchar(100) not null,
    transaction_id uuid not null,
    correlation_id varchar(100) not null,
    causation_id varchar(100) not null,
    reservation_id uuid,
    processed_at timestamp with time zone not null default now(),
    constraint pk_account_reservation_inbox_events primary key (event_id),
    constraint ck_account_reservation_inbox_event_type check (
        event_type in ('AccountReservationRequested.v1', 'AccountReservationReleaseRequested.v1')),
    constraint ck_account_reservation_inbox_fingerprint check (payload_fingerprint ~ '^[0-9a-f]{64}$')
);

create index idx_account_reservation_inbox_request_id
    on account_reservation_inbox_events (reservation_request_id);

create table account_reservation_event_outbox (
    event_id uuid not null,
    event_type varchar(100) not null,
    schema_version varchar(20) not null,
    producer varchar(100) not null,
    occurred_at timestamp with time zone not null,
    aggregate_id varchar(100) not null,
    correlation_id varchar(100) not null,
    causation_id varchar(100) not null,
    transaction_id uuid,
    reservation_request_id varchar(100) not null,
    reservation_id uuid,
    source_account_id uuid not null,
    destination_account_id uuid,
    amount numeric(19, 4) not null,
    currency varchar(3) not null,
    expires_at timestamp with time zone,
    status varchar(30),
    rejection_code varchar(60),
    rejection_reason varchar(500),
    release_reason varchar(60),
    posting_request_id varchar(100),
    event_status varchar(20) not null default 'PENDING',
    attempt_count integer not null default 0,
    available_at timestamp with time zone not null default now(),
    published_at timestamp with time zone,
    last_error varchar(2000),
    processing_token uuid,
    processing_until timestamp with time zone,
    json_payload text not null,
    created_at timestamp with time zone not null default now(),
    constraint pk_account_reservation_event_outbox primary key (event_id),
    constraint uq_account_reservation_event_outbox_aggregate_type unique (aggregate_id, event_type),
    constraint ck_account_reservation_event_outbox_type check (
        event_type in ('AccountReservationAccepted.v1', 'AccountReservationRejected.v1',
                       'AccountReservationReleased.v1', 'AccountReservationExpired.v1')),
    constraint ck_account_reservation_event_outbox_schema check (schema_version = '1.0.0'),
    constraint ck_account_reservation_event_outbox_status check (event_status in ('PENDING', 'PUBLISHED', 'FAILED')),
    constraint ck_account_reservation_event_outbox_attempts check (attempt_count >= 0)
);

create index idx_account_reservation_event_outbox_ready
    on account_reservation_event_outbox (event_status, available_at, created_at);
