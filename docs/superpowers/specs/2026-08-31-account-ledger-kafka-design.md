# Account Ledger Kafka Inbound Adapter Design

## Goal

Consume governed Ledger Service posting outcome facts and settle the matching Account Service reservation through the existing transport-neutral `LedgerPostingOutcomeInputPort`.

## Contract Boundary

The adapter consumes only `LedgerPostingCompleted.v1` and `LedgerPostingFailed.v1` from `.github#137`. Every record must carry `event-id`, `correlation-id`, `causation-id`, `producer`, `schema-version`, and `occurred-at` Kafka headers. The same event identity and metadata in the JSON payload must agree with the headers.

`ledger-service` is the expected producer metadata and `1.0.0` is the only supported schema version. Producer metadata is semantic validation, not authentication. The production security boundary must authenticate clients with SASL over TLS or mTLS and enforce least-privilege topic ACLs before this consumer is enabled. The topic key is `aggregateId`; Kafka ordering is guaranteed only for one key within one topic, while delivery is at least once.

Completed payloads must have a valid UUID event and posting identity, matching aggregate and posting ids, an ISO currency, balanced debit and credit lines, and positive decimal strings with at most four fractional digits. Account Service validates that exactly one debit line matches the persisted reservation account and amount, and that the payload currency matches both the reservation and account. Failed payloads have no account, currency, amount, or line fields in the governed model; they are validated only against their applicable identity and failure fields.

There is no independent reversed event. A completed event with `reversalOfLedgerEntryId` maps to the existing `REVERSED` outcome; otherwise it maps to `COMPLETED`. Failed events map to `FAILED` using the governed `postingRequestId` as the existing boundary's posting identity.

## Components

- A transport-neutral application mapper validates governed event semantics that need persisted reservation/account state and creates the unchanged `LedgerPostingOutcomeCommand`.
- Kafka DTOs and the listener parse headers and JSON, invoke the mapper, and delegate to the existing `LedgerPostingOutcomeInputPort`.
- Kafka configuration is conditional on `account.ledger.kafka.enabled`, which defaults to `false`. Helm supplies explicit SIT bootstrap, topic, group, retry, and DLQ settings.

The mapper performs no mutation. The existing outcome service continues to atomically update the account, reservation, and durable inbox event, including optimistic-lock handling and replay/conflict detection.

## Failure Handling

Malformed, unsupported, or semantically invalid records are deterministic failures. The listener does not invoke the outcome port for them and the Kafka error handler sends them directly to the governed topic-specific DLQ. Existing durable inbox conflicts, expired completions, and non-active account conflicts are also deterministic and go directly to DLQ without another balance transition.

Transient persistence and order-dependent state failures receive a bounded fixed-backoff retry. Exhausted retries are published by Spring Kafka's `DeadLetterPublishingRecoverer` to `<source-topic>.dlq`, preserving the original record and failure metadata. Kafka, not process memory, is the durable recovery surface. Authorized operators correct the root cause where needed and explicitly replay the original record; its event id makes reprocessing idempotent.

## Tests

Unit tests cover header/payload agreement, producer and schema rejection, decimal-string and line validation, outcome mapping, and valid command construction. Adapter tests cover listener delegation, replay, deterministic DLQ classification, and retryable failures. PostgreSQL integration tests exercise valid inbound mapping through the existing transactional outcome boundary and prove invalid events leave account, reservation, and inbox state unchanged.

## Scope Limits

This change does not add a public balance-mutation API, producer transport in Ledger Service, Transaction Service saga changes, topic provisioning, or an unsupported reversal event shape.
