# Account Ledger Kafka Inbound Adapter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Consume governed Ledger posting outcome events through a Kafka inbound adapter and settle reservations idempotently through the existing account outcome boundary.

**Architecture:** A transport-neutral mapper validates completed events against persisted reservation and account data before creating the existing `LedgerPostingOutcomeCommand`. A conditional Kafka listener owns wire parsing, invokes the mapper and input port, and uses bounded retries plus topic-specific durable DLQs for failures.

**Tech Stack:** Java 21, Spring Boot 4, Spring Kafka, Jackson, PostgreSQL/Flyway, Testcontainers, Helm.

**Spec:** `docs/superpowers/specs/2026-08-31-account-ledger-kafka-design.md`

## Global Constraints

- Consume only `LedgerPostingCompleted.v1` and `LedgerPostingFailed.v1` from `.github#137`.
- Require `event-id`, `correlation-id`, `causation-id`, `producer`, `schema-version`, and `occurred-at` Kafka headers and cross-check repeated payload metadata.
- Trust only `ledger-service` and schema version `1.0.0`.
- Preserve the existing `LedgerPostingOutcomeInputPort`, transactional inbox, conflict detection, and optimistic locking.
- Keep Kafka disabled by default; configure SIT explicitly through Helm.
- Send deterministic invalid or conflict events directly to a durable topic-specific DLQ; use bounded retry for transient failures.
- Do not add a public balance-mutation API, ledger producer transport, saga changes, or topic provisioning.

---

### Task 1: Governed Event Mapper

**Files:**
- Create: `src/main/java/com/digitalbank/accountservice/application/service/LedgerPostingEventMapper.java`
- Create: `src/main/java/com/digitalbank/accountservice/application/port/in/GovernedLedgerPostingEvent.java`
- Create: `src/main/java/com/digitalbank/accountservice/domain/exception/InvalidLedgerPostingEventException.java`
- Test: `src/test/java/com/digitalbank/accountservice/application/service/LedgerPostingEventMapperTest.java`

**Interfaces:**
- Consumes: governed header/payload values plus `AccountReservationRepository` and `AccountRepository`.
- Produces: `LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event)`.

- [ ] **Step 1: Write failing mapper tests**

```java
assertThat(mapper.toCommand(validCompletedEvent())).isEqualTo(
        new LedgerPostingOutcomeCommand(eventId, postingId, reservationId, COMPLETED, null));
assertThatThrownBy(() -> mapper.toCommand(eventWithInvalidDecimal())).isInstanceOf(InvalidLedgerPostingEventException.class);
```

- [ ] **Step 2: Run the mapper test to verify failure**

Run: `./mvnw -Dtest=LedgerPostingEventMapperTest test`
Expected: compilation failure because the mapper and event type do not exist.

- [ ] **Step 3: Implement the smallest transport-neutral mapper**

```java
public LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event) {
    var reservation = reservationRepository.findByReservationRequestId(event.reservationRequestId()).orElseThrow(...);
    var account = accountRepository.findById(reservation.accountId()).orElseThrow(...);
    validate(event, reservation, account);
    return event.toOutcomeCommand();
}
```

- [ ] **Step 4: Run the mapper test to verify success**

Run: `./mvnw -Dtest=LedgerPostingEventMapperTest test`
Expected: PASS.

### Task 2: Kafka Wire Adapter and Recovery

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerKafkaProperties.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerPostingKafkaListener.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerKafkaConfiguration.java`
- Create: `src/main/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerPostingEventParser.java`
- Test: `src/test/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerPostingEventParserTest.java`
- Test: `src/test/java/com/digitalbank/accountservice/adapter/in/kafka/LedgerPostingKafkaListenerTest.java`

**Interfaces:**
- Consumes: `ConsumerRecord<String, String>`, required Kafka headers, and JSON event bodies.
- Produces: an invocation of `LedgerPostingOutcomeInputPort.handle(mapper.toCommand(event))` or a typed deterministic failure.

- [ ] **Step 1: Write failing parser/listener tests**

```java
assertThat(parser.parse(recordWithHeaders(COMPLETED_JSON))).isEqualTo(completedEvent);
assertThatThrownBy(() -> parser.parse(recordWithWrongProducer())).isInstanceOf(InvalidLedgerPostingEventException.class);
verify(outcomePort).handle(expectedCommand);
```

- [ ] **Step 2: Run the adapter tests to verify failure**

Run: `./mvnw -Dtest=LedgerPostingEventParserTest,LedgerPostingKafkaListenerTest test`
Expected: compilation failure because Kafka adapter classes do not exist.

- [ ] **Step 3: Add Spring Kafka and implement conditional configuration**

```java
var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
        (record, exception) -> new TopicPartition(record.topic() + ".dlq", -1));
var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(retryDelayMs, retryAttempts - 1));
handler.addNotRetryableExceptions(InvalidLedgerPostingEventException.class, LedgerPostingOutcomeConflictException.class);
```

- [ ] **Step 4: Implement parser and listener**

```java
@KafkaListener(topics = "${account.ledger.kafka.completed-topic}", groupId = "${account.ledger.kafka.group-id}")
public void completed(ConsumerRecord<String, String> record) {
    handle(record, LedgerPostingOutcome.COMPLETED);
}
```

- [ ] **Step 5: Run adapter tests to verify success**

Run: `./mvnw -Dtest=LedgerPostingEventParserTest,LedgerPostingKafkaListenerTest test`
Expected: PASS.

### Task 3: PostgreSQL Integration and Operational Configuration

**Files:**
- Modify: `src/test/java/com/digitalbank/accountservice/AccountPersistenceIT.java`
- Modify: `src/main/resources/application.properties`
- Modify: `helm/values.yaml`
- Modify: `helm/values-sit.yaml`
- Modify: `helm/templates/deployment.yaml`
- Modify: `README.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: valid and invalid governed events through the mapper.
- Produces: no state mutation for rejected input; persisted inbox replay for valid input; Helm environment settings for listener operation.

- [ ] **Step 1: Write failing PostgreSQL and Helm assertions**

```java
assertThatThrownBy(() -> mapper.toCommand(invalidCompletedEvent())).isInstanceOf(InvalidLedgerPostingEventException.class);
assertThat(accountRepository.findById(account.id()).orElseThrow().currentBalance()).isEqualByComparingTo("100.00");
```

- [ ] **Step 2: Run integration test to verify failure**

Run: `./mvnw -Dit.test=AccountPersistenceIT verify`
Expected: compilation failure until the mapper is available, then failure until invalid-event behavior is implemented.

- [ ] **Step 3: Add safe defaults and explicit SIT settings**

```yaml
ledgerKafka:
  enabled: false
  completedTopic: ledger.posting.completed.v1
  failedTopic: ledger.posting.failed.v1
```

- [ ] **Step 4: Document partitioning, replay, DLQ recovery, and merge dependencies**

```text
Account Service consumes at least once using aggregateId as the Kafka key.
Inspect topic-specific DLQs, correct the cause, and explicitly replay the original event id.
```

- [ ] **Step 5: Run focused integration and Helm validation**

Run: `./mvnw -Dit.test=AccountPersistenceIT verify && helm lint helm --strict --values helm/values-sit.yaml && helm template account-service helm --values helm/values-sit.yaml`
Expected: PASS.

### Task 4: Full Verification and Delivery

**Files:**
- Modify: all files from Tasks 1-3 only.

- [ ] **Step 1: Run all verification**

Run: `./mvnw verify && helm lint helm --strict --values helm/values-sit.yaml && helm template account-service helm --values helm/values-sit.yaml && git diff --check`
Expected: all commands pass.

- [ ] **Step 2: Review the diff for secrets and compatibility**

Run: `git diff --check origin/feature/102-account-ledger-events...HEAD && git diff -- origin/feature/102-account-ledger-events...HEAD`
Expected: no credentials, no public balance-mutation endpoint, and no changes outside scoped adapter/configuration/documentation files.

- [ ] **Step 3: Commit and open the stacked PR**

```bash
git add .
git commit -m "feat: consume governed ledger Kafka events"
git push -u origin feature/171-account-ledger-kafka
gh pr create --base feature/102-account-ledger-events --head feature/171-account-ledger-kafka --title "feat: consume governed ledger Kafka events"
```
