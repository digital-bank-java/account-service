# Account Balance And Events

This note defines how Account Service will handle balance correctness and account events as the platform moves toward transaction and ledger workflows.

## Decision

Account Service owns account identity, lifecycle state, and account balance state. It does not expose public APIs that directly mutate balances.

Balance changes must be driven by transaction or ledger-controlled workflows. Account Service will later expose internal application behavior for reserving, posting, releasing, or reversing balances, but those operations must be reached through trusted orchestration and event flows, not by customer-facing balance update endpoints.

## Why Public Balance Mutation APIs Are Not Allowed

Direct public balance mutation endpoints create unacceptable risk in a banking system:

- They bypass transaction authorization and fraud controls.
- They make double-spend protection harder to enforce consistently.
- They can create balance changes without a durable business movement.
- They make audit reconstruction weaker because the balance change becomes the primary event instead of the result of a validated financial operation.

The account balance must be a controlled projection of approved financial movements, not a field that external clients can patch.

## Balance Model

Each account keeps:

- `current_balance`: posted balance after settled movements.
- `available_balance`: balance available for new debits after active reservations.
- `version`: optimistic locking value used to detect concurrent updates.

The initial account lifecycle slice starts both balances at zero.

Future balance operations should use the following model:

| Operation | Purpose | Balance effect |
| --- | --- | --- |
| Reserve funds | Hold money for a pending debit | Decrease available balance only |
| Release reservation | Cancel an unused hold | Increase available balance only |
| Post debit | Settle an outgoing movement | Decrease current balance and consume reservation if one exists |
| Post credit | Settle incoming funds | Increase current and available balance |
| Reverse movement | Correct a previously posted movement | Apply the opposite accounting effect through a linked reversal |

Every balance operation must be linked to a durable movement record. Balance updates without a movement record are not acceptable.

## Required Future Tables

The current `accounts` table is enough for account opening and lookup. Balance mutation will require additional tables before implementation.

Expected future tables:

| Table | Purpose |
| --- | --- |
| `account_movements` | Immutable record of posted debits, credits, reversals, and adjustments |
| `account_reservations` | Active or released holds against available balance |
| `account_idempotency_keys` | Deduplicate retryable account commands |
| `account_outbox_events` | Transactional outbox for Kafka publication |
| `account_inbox_events` | Deduplicate consumed Kafka events when Account Service later consumes events |

The exact schema should be introduced in a dedicated Flyway migration with tests before any balance mutation behavior is implemented.

## Idempotency

Retryable write operations must include an idempotency key.

Examples:

- Account opening uses `openingRequestId`.
- Future reservation requests should use a reservation request id.
- Future posting requests should use a transaction or ledger movement id.

The service must return the original result for a duplicate idempotency key instead of creating a second account, reservation, movement, or event.

## Optimistic Locking

Balance updates must use optimistic locking through the account `version` field.

Expected behavior:

1. Read the account and current version.
2. Validate the requested balance operation.
3. Write the balance change with the expected version.
4. Reject or retry when another transaction changed the same account first.

This is similar to compare-and-swap semantics. It prevents two concurrent requests from silently overwriting each other.

## Kafka Events

Account Service should publish lifecycle and balance-related events through an outbox-backed Kafka flow.

Initial events:

| Event | Trigger |
| --- | --- |
| `AccountOpened` | A new account is created |
| `AccountStatusChanged` | Account status changes |
| `AccountBalanceChanged` | A posted movement changes account balance |
| `AccountReservationCreated` | Funds are reserved |
| `AccountReservationReleased` | Reserved funds are released |

Kafka publication must not happen directly inside the request handler. The database write and outbox insert should happen in the same transaction. A separate publisher later reads the outbox and publishes to Kafka.

## Outbox And Inbox Strategy

Use the transactional outbox pattern for events produced by Account Service:

```text
Application command
  -> database transaction
       -> update account state
       -> insert movement or reservation
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

1. Add `account_movements`, `account_reservations`, and outbox tables.
2. Add movement and reservation domain models.
3. Add internal input ports for reservation, posting, release, and reversal.
4. Add service methods with optimistic locking and idempotency.
5. Add integration tests for concurrent updates and duplicate idempotency keys.
6. Add outbox publisher and Kafka topic contracts.

This sequence keeps balance correctness ahead of messaging and public exposure.
