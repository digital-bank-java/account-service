# Account Ledger Outcome Handling Design

## Goal

Add a transport-neutral Account Service application boundary that applies ledger posting outcomes to an existing account reservation without exposing a balance mutation endpoint or adding Kafka infrastructure.

## Context

The foundation branch `feature/102-account-reservations` persists an `ACTIVE` reservation and decreases an account's `available_balance` in the same transaction. This change builds on that boundary and is intended to merge after the foundation PR `account-service#33`.

The governed ledger event contract is not merged yet. The application boundary therefore owns only a neutral command and result model. A future Kafka adapter can translate the final contract into this command without changing reservation or balance behavior.

## Application Boundary

The input port accepts:

- `eventId`: stable consumed-event identity.
- `ledgerPostingId`: posting identity for the outcome.
- `reservationRequestId`: reservation correlation key.
- `outcome`: `COMPLETED`, `FAILED`, or `REVERSED`.
- `originalPostingId`: required only for `REVERSED`; it identifies the completed posting being reversed.

Account, currency, and amount are deliberately not part of the command. The handler loads the persisted reservation and validates all balance-affecting data against it. This prevents an event payload from changing the amount or account that was originally reserved.

## State Transitions

| Current reservation | Outcome | Effect | Result |
| --- | --- | --- | --- |
| `ACTIVE` | `COMPLETED` | Decrease `current_balance`; consume the hold already removed from `available_balance` | `COMMITTED` |
| `ACTIVE` | `FAILED` | Restore the reserved amount to `available_balance` | `RELEASED` |
| `COMMITTED` | `REVERSED` | Increase `current_balance` and `available_balance` by the original reservation amount | `REVERSED` |

The handler rejects out-of-order or conflicting transitions. A reversal must reference the posting recorded by the successful completion, so an event received before completion remains retryable rather than mutating state.

## Idempotency And Transactions

`account_inbox_events` stores the event payload and processing timestamp with a unique `event_id`. The inbox insert and account/reservation update occur in one database transaction. A duplicate event ID with the same payload returns an idempotent result; the same event ID with a different payload is rejected.

The reservation also stores its initial `ledger_posting_id` and, when reversed, its `reversed_by_ledger_posting_id`. These fields provide correlation-level protection if equivalent delivery is presented under another event ID and prevent a posting from applying two different outcomes.

Optimistic locking remains on both the account and reservation entities. Concurrent duplicate deliveries may cause one transaction to lose the optimistic-lock race; the handler then rechecks the inbox and returns the already-processed result.

## Persistence Changes

Migration `V3__add_ledger_outcome_inbox.sql` will:

- expand reservation status constraints and add posting correlation columns;
- add `account_inbox_events` with a primary key on `event_id`;
- retain append-only event history and existing account/reservation foreign-key and balance constraints.

No balance projection table, outbox, Kafka dependency, controller, or public balance mutation API is introduced in this slice.

## Testing And Assumptions

Unit tests cover state transitions, payload validation, duplicate event replay, correlation-level replay, conflicting outcomes, and out-of-order reversal rejection. PostgreSQL/Testcontainers tests verify Flyway schema changes, transactional persistence, rollback, and duplicate deliveries against real optimistic-locking entities.

The current reservation amount is the authoritative amount for all outcomes. The ledger event is assumed to identify exactly one reservation through `reservationRequestId`; the future transport adapter is responsible for mapping the governed event contract to this boundary.
