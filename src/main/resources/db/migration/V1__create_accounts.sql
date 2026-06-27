create table accounts (
    id uuid not null,
    customer_id uuid not null,
    account_number varchar(34) not null,
    iban varchar(34),
    account_type varchar(30) not null,
    currency varchar(3) not null,
    status varchar(30) not null,
    current_balance numeric(19, 4) not null default 0,
    available_balance numeric(19, 4) not null default 0,
    opening_request_id varchar(100),
    version bigint not null default 0,
    created_at timestamp with time zone not null default now(),
    updated_at timestamp with time zone not null default now(),
    closed_at timestamp with time zone,
    constraint pk_accounts primary key (id),
    constraint uq_accounts_account_number unique (account_number),
    constraint uq_accounts_iban unique (iban),
    constraint uq_accounts_opening_request_id unique (opening_request_id),
    constraint ck_accounts_type check (account_type in ('CURRENT', 'SAVINGS')),
    constraint ck_accounts_status check (status in ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    constraint ck_accounts_currency_format check (currency = upper(currency)),
    constraint ck_accounts_current_balance_non_negative check (current_balance >= 0),
    constraint ck_accounts_available_balance_non_negative check (available_balance >= 0)
);

create index idx_accounts_customer_id on accounts (customer_id);
