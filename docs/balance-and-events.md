# Account Balance And Events

This note defines how Account Service will handle balance correctness and account events as the platform moves toward transaction, ledger, and Kafka workflows.

## Decision

Account Service owns account identity, lifecycle state, available-balance reservation, and account balance projection state. It does not expose public APIs that directly mutate balances.

Final posted balance changes must be driven by Ledger Service events. Transaction Service may orchestrate a transfer saga, and Account Service may reserve or release available funds, but Transaction Service must not directly tell Account Service to post the final debit-credit effect.

The target ownership model is:

| Capability | Owner |
| --- | --- |
| Transfer workflow and status | Transaction Service |
| Available-balance reservation | Account Service |
| Official debit and credit postings | Ledger Service |
| Account balance projection update | Account Service, triggered by ledger posting events |
| Event contract governance | Event contracts / schema governance |

## Why Public Balance Mutation APIs Are Not Allowed

Direct public balance mutation endpoints create unacceptable risk in a banking system:

- They bypass transaction authorization and fraud controls.
- They make double-spend protection harder to enforce consistently.
- They can create balance changes without a durable ledger posting.
- They make audit reconstruction weaker because the balance change becomes the primary event instead of the result of a validated financial operation.

The account balance must be a controlled projection of approved ledger postings, not a field that external clients can patch.

## Balance Model

Each account keeps:

- `current_balance`: posted balance after settled movements.
- `available_balance`: balance available for new debits after active reservations.
- `version`: optimistic locking value used to detect concurrent updates.

The initial account lifecycle slice starts both balances at zero.

Future balance behavior should use the following model:

| Operation | Purpose | Balance effect |
| --- | --- | --- |
| Reserve funds | Hold money for a pending debit | Decrease available balance only |
| Release reservation | Cancel an unused hold | Increase available balance only |
| Commit debit reservation | Apply a completed ledger posting to a reserved debit | Decrease current balance and consume reservation |
| Apply credit posting | Project a completed ledger credit | Increase current and available balance |
| Apply reversal posting | Project a completed ledger reversal | Apply the opposite projection through the linked ledger posting |

Every balance projection change must be linked to a durable ledger posting. Balance updates without a ledger posting are not acceptable.

## Saga Direction

The target transfer flow is:

```text
Transaction Service
  -> create transfer PENDING
  -> request Account Service reservation

Account Service
  -> validate available balance
  -> create reservation
  -> decrease available balance
  -> publish AccountReservationCreated through outbox

Transaction Service
  -> request Ledger Service posting

Ledger Service
  -> create immutable debit and credit entries
  -> publish LedgerPostingCompleted or LedgerPostingFailed through outbox

Account Service
  -> consume ledger posting event through inbox
  -> commit reservation, apply credit, or release reservation

Transaction Service
  -> consume ledger posting event through inbox
  -> mark transfer COMPLETED or FAILED
```

This keeps Transaction Service as the saga orchestrator without making it the balance posting authority.

## Required Future Tables

The current `accounts` table is enough for account opening and lookup. Balance mutation will require additional tables before implementation.

Expected future Account Service tables:

| Table | Purpose |
| --- | --- |
| `account_reservations` | Active or released holds against available balance |
| `account_idempotency_keys` | Deduplicate retryable account commands |
| `account_balance_projection_entries` | Account-side projection records linked to ledger posting ids |
| `account_outbox_events` | Transactional outbox for Kafka publication |
| `account_inbox_events` | Deduplicate consumed ledger events |

Expected future Ledger Service tables:

| Table | Purpose |
| --- | --- |
| `ledger_postings` | Posting header with transaction id, status, idempotency key, and correlation ids |
| `ledger_entries` | Immutable balanced debit and credit entries |
| `ledger_outbox_events` | Transactional outbox for ledger posting events |

The exact schema should be introduced in a dedicated Flyway migration with tests before any balance mutation behavior is implemented.

## Idempotency

Retryable write operations must include an idempotency key.

Examples:

- Account opening uses `openingRequestId`.
- Future reservation requests should use a reservation request id.
- Future ledger posting requests should use a transaction id and posting idempotency key.
- Future consumed ledger events should use event id and ledger posting id for inbox deduplication.

The service must return the original result for a duplicate idempotency key instead of creating a second account, reservation, movement, or event.

## Optimistic Locking

Reservation and balance projection updates must use optimistic locking through the account `version` field.

Expected behavior:

1. Read the account and current version.
2. Validate the requested reservation or ledger-driven projection operation.
3. Write the reservation/projection change with the expected version.
4. Reject or retry when another transaction changed the same account first.

This is similar to compare-and-swap semantics. It prevents two concurrent requests from silently overwriting each other.

## Kafka Events

Account Service should publish lifecycle, reservation, and projection events through an outbox-backed Kafka flow.

Initial events:

| Event | Trigger |
| --- | --- |
| `AccountOpened` | A new account is created |
| `AccountStatusChanged` | Account status changes |
| `AccountBalanceChanged` | A ledger posting changes the account balance projection |
| `AccountReservationCreated` | Funds are reserved |
| `AccountReservationReleased` | Reserved funds are released |

Account Service should also consume these ledger events:

| Event | Account Service reaction |
| --- | --- |
| `LedgerPostingCompleted` | Commit debit reservation and/or apply credit projection |
| `LedgerPostingFailed` | Release related reservation |

Kafka publication must not happen directly inside the request handler. The database write and outbox insert should happen in the same transaction. A separate publisher later reads the outbox and publishes to Kafka.

## Outbox And Inbox Strategy

Use the transactional outbox pattern for events produced by Account Service:

```text
Application command
  -> database transaction
       -> update account state
       -> insert reservation or projection entry
       -> insert outbox event
  -> outbox publisher
       -> publish event to Kafka
       -> mark outbox event as published
```

Use an inbox table for events consumed by Account Service later:

```text
Kafka event received
  -> check inbox event id
  -> ignore if already processed
  -> process event in database transaction
  -> record inbox event id
```

This protects against duplicate Kafka delivery and retry behavior.

## Implementation Sequence

Recommended future work:

1. Bootstrap Ledger Service as the official posting owner.
2. Add Account Service reservation, inbox, outbox, and projection tables.
3. Add Ledger Service posting, entry, and outbox tables.
4. Add reservation domain behavior in Account Service.
5. Add immutable double-entry posting behavior in Ledger Service.
6. Add ledger posting events and governed Kafka contracts.
7. Add Account Service consumers for ledger posting completion/failure.
8. Add Transaction Service saga orchestration and event consumers.
9. Add integration tests for concurrent reservations, duplicate idempotency keys, duplicate events, and ledger/account reconciliation.

This sequence keeps balance correctness ahead of messaging and public exposure.
