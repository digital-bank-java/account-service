# Account Reservation Transport Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add governed local/core reservation command consumption and durable reservation fact publication to Account Service.

**Architecture:** Transport-neutral reservation request/release application ports sit between strict Kafka parsers and the existing reservation services. A PostgreSQL inbox protects command delivery, while a transactionally written generic outbox stores exact governed fact payloads for a leased scheduled relay.

**Tech Stack:** Java 21, Spring Boot 4, Spring Kafka, Jackson, PostgreSQL/Flyway, JPA, Testcontainers, Maven.

**Spec:** `docs/superpowers/specs/2026-09-01-account-reservation-transport-design.md`

## Global Constraints

- Consume only `AccountReservationRequested.v1` and `AccountReservationReleaseRequested.v1`.
- Publish only `AccountReservationAccepted.v1`, `AccountReservationRejected.v1`, `AccountReservationReleased.v1`, and `AccountReservationExpired.v1`.
- Require the six governed Kafka headers and exact header/payload identity agreement.
- Keep Kafka disabled by default and permit plaintext only through an explicit SIT allow flag.
- Preserve optimistic locking, unique reservation request safeguards, and existing ledger outcome behavior.
- Keep all state changes and corresponding outbox rows in one database transaction.
- Do not implement AWS/UAT/PROD, topic provisioning, saga changes, or public APIs.

### Task 1: Add failing transport/application boundary tests

**Files:**
- Create focused tests under `src/test/java/com/digitalbank/accountservice/application/service/` and `src/test/java/com/digitalbank/accountservice/adapter/in/kafka/`.

- [ ] Write tests for valid request mapping, invalid envelope/header/payload rejection, release validation, exact duplicate no-op, conflicting event-id rejection, business rejection fact, and accepted fact data.
- [ ] Run the focused tests and confirm they fail because the new types and ports are absent.

### Task 2: Implement request/release models, inbox, and atomic application services

**Files:**
- Create transport-neutral command/result records and ports in `application/port/in`.
- Create inbox ports/entities/mappers/adapters in `application/port/out` and `adapter/out/persistence`.
- Modify `AccountReservationService`, `AccountReservationJpaEntity`, `AccountReservationJpaMapper`, and `ReservationView`.
- Add additive Flyway migration for destination, accepted-event metadata, reservation command inbox, and reservation outbox prerequisites.

- [ ] Add compatibility constructors while making governed requests carry transaction, destination, and source identifiers.
- [ ] Implement exact replay and deterministic conflict checks using stored canonical fingerprint and business identity.
- [ ] Make acceptance/rejection and inbox/outbox recording atomic; map known domain failures to stable rejection codes.
- [ ] Add release application behavior that releases ACTIVE reservations once and treats matching terminal replay as a no-op.
- [ ] Run the focused application tests and `AccountPersistenceIT`.

### Task 3: Implement governed durable outbox and relay

**Files:**
- Create outbox port/entity/mapper/repository, event payload model, relay service, and configuration under existing account-service packages.
- Modify `application.properties` and add the outbox Flyway migration.

- [ ] Persist exact JSON payload plus headers/metadata, with unique event and aggregate/type safeguards.
- [ ] Implement leased claim, publish acknowledgement, retry scheduling, and lease-safe terminal updates.
- [ ] Add relay tests for successful publication, broker failure, retry timestamp, and stale lease protection.

### Task 4: Add Kafka command adapters and integrate ledger/expiry facts

**Files:**
- Create reservation Kafka properties/configuration/parser/listener classes.
- Modify existing ledger Kafka configuration/listener wiring, `AccountLedgerOutcomeService`, `AccountReservationExpiryService`, and application configuration.
- Add focused Kafka security/parser/listener tests and persistence assertions.

- [ ] Parse request and release topics with topic-specific DLQ classification and bounded retry.
- [ ] Add accepted/rejected/released/expired event construction with correct causation and partition key.
- [ ] Ensure `LedgerPostingFailed` emits exactly one released fact and expiry emits exactly one expired fact.
- [ ] Keep listener methods transport-only and Kafka beans conditional on explicit enablement.

### Task 5: Verify and commit

- [ ] Run focused unit tests, `./mvnw verify`, and `git diff --check`.
- [ ] Inspect the diff for scope, secrets, production plaintext defaults, and transactional gaps.
- [ ] Commit with `feat: add governed account reservation transport` and record the commit hash.
