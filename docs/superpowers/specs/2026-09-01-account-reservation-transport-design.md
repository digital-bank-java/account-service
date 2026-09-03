# Account Reservation Transport Design

## Goal

Implement the Account Service side of the governed Sprint 3 reservation
transport for local/core SIT only. Account Service consumes reservation request
and release-request commands, owns reservation state, and publishes accepted,
rejected, released, and expired facts through a durable outbox.

## Boundaries

The Kafka adapters validate the governed envelope, required headers, producer,
schema version, event type, identifiers, payload shape, amount, currency, and
release reason. They delegate valid messages to transport-neutral application
ports. Business rules remain in the existing reservation and ledger outcome
services.

The request inbox is unique on `event_id` and stores a canonical payload
fingerprint plus request identity fields. Exact redelivery is a successful
no-op. Reuse of an event id with different bytes or identity is a deterministic
conflict and is routed to the source topic DLQ. `reservationRequestId` remains a
business idempotency key and conflicting reuse is rejected by the existing
reservation safeguards.

## Atomic State And Facts

Acceptance or business rejection, request-inbox recording, reservation/account
mutation, and the corresponding outbox row occur in one database transaction.
Release requests use the reservation application service. Ledger failure keeps
the existing ledger inbox and state transition, while adding one released fact
with `LEDGER_POSTING_FAILED` atomically. Expiry changes only ACTIVE rows, restores
availability, and creates one expired fact in the same transaction.

The reservation stores `destination_account_id`; the accepted fact reads it
from the reservation and the rejected fact reads it from the request inbox.
Accepted event identity is persisted with the reservation so expiry can use it
as causation. Existing constructor call sites remain source-compatible through
compatibility constructors for non-transport tests.

## Outbox And Security

The outbox stores the complete governed JSON payload and metadata, has unique
event identity/aggregate safeguards, and uses leased claims with bounded retry.
The scheduled relay sends the stored payload with required headers and marks
rows published only after broker acknowledgement. Kafka transport configuration
is conditional and disabled by default. Enabling insecure `PLAINTEXT` or
`SASL_PLAINTEXT` requires an explicit allow flag intended only for SIT; otherwise
enabled transport requires authenticated TLS transport.

No AWS, UAT, PROD, topic provisioning, shared DTOs, public API, or transaction
saga changes are included.

## Verification

Focused tests cover parser validation, exact/conflicting inbox replay, atomic
outbox behavior, relay lease/retry handling, release idempotency, ledger-failure
release, and expiry exactly once. The final gate is `./mvnw verify`.
