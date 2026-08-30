# AGENTS.md

## Repository Purpose

`account-service` owns account lifecycle state and account retrieval.

It models accounts as banking resources but does not directly own final financial posting workflows.

## Current Responsibilities

- open accounts
- retrieve one account
- list accounts for admin/internal query use
- persist account state in PostgreSQL
- apply transport-neutral ledger posting outcomes to persisted reservations

## Current Non-Responsibilities

- direct public balance mutation APIs
- ledger posting
- transaction saga orchestration
- payment execution
- Kafka transport adapters for ledger posting events until the governed contract is merged

## Architecture

- Hexagonal architecture
- Domain and invariants stay in `domain`
- Application orchestration stays in `application.service`
- Ports define boundaries
- Adapters implement transport and persistence

## Key Commands

```bash
./mvnw test
./mvnw verify
./mvnw spring-boot:run
docker build -t digital-bank-java/account-service:<tag> .
helm lint helm --strict
```

## Runtime and Data

- Default service port: `8082`
- Logical database in SIT: `account_service`
- Runtime configuration comes from `config-repo`
- Database credentials must remain externalized

## Domain Direction

- Balance correctness should eventually be driven by transaction and ledger workflows.
- Reservation, posting, and completion flows should be designed with idempotency and optimistic locking in mind.
- Ledger outcomes must update the account, reservation, and consumed-event record in one transaction.
- Public customer-facing APIs should not directly post balance changes.

## Deployment Notes

- Gateway is the normal access path in SIT.
- If a new image is built locally for SIT, rebuild, then perform rollout restart and rollout status checks.

## Working Rules

- Do not implement shortcut balance updates just to simplify a flow.
- Keep admin query APIs clearly separated from customer-facing APIs.
- If a change affects transaction or ledger integration, create or link the cross-repo tracking issue first.
