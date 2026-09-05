alter table account_inbox_events
    add column destination_account_id uuid;

alter table account_inbox_events
    add constraint fk_account_inbox_events_destination_account
        foreign key (destination_account_id) references accounts (id),
    add constraint ck_account_inbox_events_failed_destination
        check (outcome <> 'FAILED' or destination_account_id is null);
