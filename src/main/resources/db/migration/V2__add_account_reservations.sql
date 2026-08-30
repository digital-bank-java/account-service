create table account_reservations (
    id uuid not null,
    account_id uuid not null,
    reservation_request_id varchar(100) not null,
    currency varchar(3) not null,
    amount numeric(19, 4) not null,
    correlation_id varchar(100) not null,
    causation_id varchar(100) not null,
    status varchar(30) not null,
    expires_at timestamp with time zone not null,
    version bigint not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    constraint pk_account_reservations primary key (id),
    constraint uq_account_reservations_request_id unique (reservation_request_id),
    constraint fk_account_reservations_account foreign key (account_id) references accounts (id),
    constraint ck_account_reservations_request_id_non_blank check (length(btrim(reservation_request_id)) > 0),
    constraint ck_account_reservations_correlation_id_non_blank check (length(btrim(correlation_id)) > 0),
    constraint ck_account_reservations_causation_id_non_blank check (length(btrim(causation_id)) > 0),
    constraint ck_account_reservations_currency_format check (currency ~ '^[A-Z]{3}$'),
    constraint ck_account_reservations_amount_positive check (amount > 0),
    constraint ck_account_reservations_status check (status in ('ACTIVE')),
    constraint ck_account_reservations_version_non_negative check (version >= 0),
    constraint ck_account_reservations_timestamps check (updated_at >= created_at and expires_at > created_at)
);

create index idx_account_reservations_account_id on account_reservations (account_id);
