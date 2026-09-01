# Account Ledger Outcomes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply completed, failed, and reversed ledger posting outcomes to persisted account reservations through a transport-neutral, idempotent application boundary.

**Architecture:** Extend the existing reservation state and persistence boundary with terminal states and posting correlation metadata. Implement `LedgerPostingOutcomeInputPort` in a transactional application service that claims a durable inbox event and updates the account plus reservation atomically; do not add Kafka or HTTP adapters.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, PostgreSQL, Flyway, JUnit 5, AssertJ, Testcontainers PostgreSQL, Maven, Helm.

**Spec:** `docs/superpowers/specs/2026-08-30-account-ledger-outcomes-design.md`

## Global Constraints

- Preserve the existing `feature/102-account-reservations` repository boundary and optimistic-locking behavior.
- The input command contains only `eventId`, `ledgerPostingId`, `reservationRequestId`, `outcome`, and optional `originalPostingId`.
- Account, currency, and amount come from the persisted reservation, never from the event command.
- `COMPLETED` commits a reservation, `FAILED` releases it, and `REVERSED` reverses a prior committed posting.
- Duplicate event IDs and equivalent posting correlations must not apply a transition twice.
- Do not add Kafka dependencies, Kafka listeners, public balance mutation endpoints, outbox infrastructure, or unrelated refactors.

---

### Task 1: Define the neutral outcome and persistence ports

**Files:**
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/LedgerPostingOutcome.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/LedgerPostingOutcomeCommand.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/LedgerPostingOutcomeResult.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/LedgerPostingOutcomeInputPort.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/InboxEventView.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/out/AccountInboxEventRepository.java`
- Modify: `src/main/java/com/digitalbank/accountservice/application/port/out/AccountReservationRepository.java`
- Modify: `src/main/java/com/digitalbank/accountservice/application/port/in/ReservationView.java`
- Modify: `src/main/java/com/digitalbank/accountservice/domain/model/ReservationStatus.java`
- Create: `src/main/java/com/digitalbank/accountservice/domain/exception/LedgerPostingOutcomeConflictException.java`
- Create: `src/main/java/com/digitalbank/accountservice/domain/exception/ReservationStateConflictException.java`
- Test: `src/test/java/com/digitalbank/accountservice/application/port/in/LedgerPostingOutcomeCommandTest.java`

**Interfaces:**
- `LedgerPostingOutcomeInputPort.handle(LedgerPostingOutcomeCommand)` returns `LedgerPostingOutcomeResult`.
- `AccountInboxEventRepository.findByEventId(String)`, `findByLedgerPostingId(String)`, and `save(LedgerPostingOutcomeCommand, Instant)` expose durable inbox operations.
- `AccountReservationRepository.save(ReservationView)` updates an existing reservation using its JPA version.
- `ReservationView` carries nullable `ledgerPostingId` and `reversedByLedgerPostingId` metadata.

- [ ] **Step 1: Write the failing command validation tests** for blank IDs, unsupported reversal metadata, and required original posting reference.
- [ ] **Step 2: Run the focused test** with `./mvnw -q -Dtest=LedgerPostingOutcomeCommandTest test`; expect compilation failure because the command does not exist.
- [ ] **Step 3: Add the records, enum, statuses, exceptions, and port signatures** with trimmed nonblank identifiers and the exact transition vocabulary.
- [ ] **Step 4: Run the focused test** and verify it passes.
- [ ] **Step 5: Commit** with `git add src/main src/test && git commit -m "feat: define ledger outcome application boundary"`.

### Task 2: Implement reservation outcome transitions with unit tests

**Files:**
- Modify: `src/main/java/com/digitalbank/accountservice/application/service/AccountReservationService.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/service/AccountLedgerOutcomeService.java`
- Create: `src/test/java/com/digitalbank/accountservice/application/service/AccountLedgerOutcomeServiceTest.java`

**Interfaces:**
- `AccountLedgerOutcomeService` implements `LedgerPostingOutcomeInputPort` and receives `AccountRepository`, `AccountReservationRepository`, `AccountInboxEventRepository`, `Clock`, and `PlatformTransactionManager`.
- The service updates the account first, updates the existing reservation second, and records the inbox event in the same transaction.

- [ ] **Step 1: Add failing unit tests** for completion (`current_balance - amount`, `COMMITTED`), failure (`available_balance + amount`, `RELEASED`), reversal after completion (`current_balance + amount`, `available_balance + amount`, `REVERSED`), exact event replay, same-posting replay, conflicting event payload, and out-of-order reversal.
- [ ] **Step 2: Run `./mvnw -q -Dtest=AccountLedgerOutcomeServiceTest test`** and verify the tests fail for missing service behavior.
- [ ] **Step 3: Implement the smallest transactional handler**: check/replay the inbox, load the reservation by request ID, validate posting correlation and state, calculate the account projection from the persisted amount, save the account/reservation, then save the inbox record.
- [ ] **Step 4: Catch concurrent optimistic-lock or unique-event failures only to re-read the inbox/correlation and return the committed idempotent result; otherwise rethrow.**
- [ ] **Step 5: Run the focused unit test and the existing reservation unit tests; verify all pass.**
- [ ] **Step 6: Commit** with `git add src/main src/test && git commit -m "feat: handle ledger posting outcomes idempotently"`.

### Task 3: Add Flyway/JPA inbox and terminal reservation persistence

**Files:**
- Create: `src/main/resources/db/migration/V3__add_ledger_outcome_inbox.sql`
- Modify: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/AccountReservationJpaEntity.java`
- Modify: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/AccountReservationJpaMapper.java`
- Modify: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/PostgresAccountReservationRepository.java`
- Modify: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/SpringDataAccountReservationRepository.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/AccountInboxEventJpaEntity.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/AccountInboxEventJpaMapper.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/PostgresAccountInboxEventRepository.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/out/persistence/SpringDataAccountInboxEventRepository.java`
- Modify: `src/test/java/com/digitalbank/accountservice/AccountPersistenceIT.java`

**Interfaces:**
- PostgreSQL stores reservation posting metadata, valid terminal statuses, and inbox payload fields under Flyway migration V3.
- JPA repositories map the application views without exposing adapter types outside the persistence package.

- [ ] **Step 1: Add failing Testcontainers tests** for completion persistence, failure persistence, reversal persistence, duplicate event replay, same-posting replay, conflicting event rejection, and transaction rollback when inbox persistence fails.
- [ ] **Step 2: Run the selected integration tests** with `./mvnw -q -Dtest=AccountPersistenceIT -Dfailsafe.includes=**/AccountPersistenceIT.java verify`; verify the new tests fail before V3/JPA support exists.
- [ ] **Step 3: Add V3 constraints and tables**: allow `ACTIVE`, `COMMITTED`, `RELEASED`, `REVERSED`; add nullable initial and reversal posting IDs with uniqueness; create `account_inbox_events` keyed by `event_id` with outcome payload and processed timestamp.
- [ ] **Step 4: Implement JPA entities, mappers, Spring Data methods, and repository adapters** using `saveAndFlush` so optimistic locking and transaction rollback are observable in tests.
- [ ] **Step 5: Run `./mvnw -q -Dtest=AccountPersistenceIT -Dfailsafe.includes=**/AccountPersistenceIT.java verify`** and verify the integration tests pass with PostgreSQL.
- [ ] **Step 6: Commit** with `git add src/main src/test && git commit -m "feat: persist account ledger outcome inbox"`.

### Task 4: Document workflow and validate repository artifacts

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `docs/balance-and-events.md`
- Modify: `.github/PULL_REQUEST_TEMPLATE.md` only if the account-service repository contains a local template; otherwise use the repository template during PR creation.

- [ ] **Step 1: Document** the neutral boundary, supported state transitions, inbox idempotency, assumption that the final event contract is pending `.github#137` / `ledger-service#14`, and required merge order after `account-service#33`.
- [ ] **Step 2: Run `git diff --check` and scan for Kafka imports/controllers** to verify scope remains transport-neutral.
- [ ] **Step 3: Run `./mvnw verify`.
- [ ] **Step 4: Run `helm lint helm --strict` and `helm template account-service helm --values helm/values-sit.yaml`.
- [ ] **Step 5: Review the complete diff and commit** with `git add README.md AGENTS.md docs/balance-and-events.md && git commit -m "docs: document account ledger outcome handling"`.

### Task 5: Push and create/update the non-draft pull request

**Files:**
- Modify: remote branch `feature/102-account-ledger-events`

- [ ] **Step 1: Re-run required verification from the final commit**: `./mvnw verify`, `helm lint helm --strict`, `helm template account-service helm --values helm/values-sit.yaml`, and `git diff --check origin/main...HEAD`.
- [ ] **Step 2: Push** with `git push --set-upstream origin feature/102-account-ledger-events`.
- [ ] **Step 3: Create a non-draft stacked PR** targeting `feature/102-account-reservations`, with valid Markdown linking `.github#102` and `account-service#33`, and stating that the foundation PR must merge into `main` before this PR is retargeted and merged; state that Kafka adapter work waits for `.github#137` / `ledger-service#14`.
- [ ] **Step 4: Verify through `gh pr view`** that the PR is open, non-draft, targets the intended base, and contains the required links and merge-order text.
