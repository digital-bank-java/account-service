# Account Service

Account lifecycle and account lookup service for the Digital Bank Java platform.

## Responsibilities

- Own account identity and account lifecycle state.
- Expose account creation and lookup capabilities.
- Keep account data inside the Account Service boundary.
- Prepare the account domain for later transaction-controlled balance workflows.

## Ledger Outcome Boundary

Account Service exposes a transport-neutral application input boundary for ledger posting outcomes. It commits an active reservation on `COMPLETED`, releases it on `FAILED`, and applies the inverse projection on `REVERSED`. New reservations and every ledger-driven monetary transition require an `ACTIVE` account; `SUSPENDED` and `CLOSED` accounts reject them. The account, currency, and amount are loaded from the persisted reservation; they are not accepted from the outcome input.

The outcome handler records consumed event identity in the database with the reservation transition, so duplicate deliveries are replayed without applying a balance change twice. It does not expose a public balance mutation endpoint.

Active reservations are expired by a scheduled transactional sweeper. Each batch locks due rows with PostgreSQL `FOR UPDATE SKIP LOCKED`, restores available balance, and records the terminal `EXPIRED` state. Expiry cleanup runs for every account status because it removes an invalid hold rather than initiating new activity. A `COMPLETED` outcome processed at or after `expiresAt`, including after the sweep, is rejected and cannot debit the account.

## Governed Ledger Kafka Inbound Adapter

When `ACCOUNT_LEDGER_KAFKA_ENABLED=true`, Account Service consumes only the governed `LedgerPostingCompleted.v1` and `LedgerPostingFailed.v1` topics. It requires and cross-checks the `event-id`, `correlation-id`, `causation-id`, `producer`, `schema-version`, and `occurred-at` headers against the payload; the expected producer metadata is `ledger-service` and the supported schema is `1.0.0`.

The `producer` header and payload field are semantic metadata, not authentication and not a trust anchor. Production rollout is blocked until the Kafka platform authenticates the Ledger Service client with SASL over TLS or mTLS, encrypts broker traffic, and enforces ACLs that allow the Account Service identity to read only the governed source topics and write only their DLQs. `ACCOUNT_LEDGER_KAFKA_SECURITY_PROTOCOL` configures the Kafka transport protocol; SASL and SSL client properties and all credentials/key material must be supplied through Config Server and the approved secret integration using `spring.kafka.properties`, never committed here. SIT currently uses `PLAINTEXT` and must not be treated as the production security boundary.

Kafka records are keyed by the governed `aggregateId`. Ordering applies only to one key in one topic, so Account Service treats delivery as at least once and retains the existing PostgreSQL inbox for idempotent replay and conflict detection. A completed event must contain a balanced debit/credit posting with one debit that matches the persisted reservation account, currency, and decimal-string amount. When the reservation represents a transfer, it must also contain one matching counter-line for the persisted destination account; the destination credit is applied atomically with the source debit, and a reversal applies the opposite projection. A completed event containing `reversalOfLedgerEntryId` uses the existing reversed-outcome behavior. Failed events carry no account, currency, amount, or line fields in the governed contract and use `postingRequestId` as the posting identity.

The listener is disabled by default. SIT Helm values explicitly configure the Kafka bootstrap address, transport protocol, topics, consumer group, retry attempts, and retry delay. Invalid producer metadata, unsupported schemas, malformed events, expired completions, account-status conflicts, and inbox conflicts do not mutate account state and are sent directly to the source topic's durable `.dlq` topic. Transient persistence or ordering failures use bounded retry before the same DLQ recovery. Operators inspect the original event and exception metadata in the DLQ, correct the cause where necessary, then explicitly replay the original record. Replay preserves `event-id`, so the database inbox makes a successful prior delivery safe.

This repository does not provision a Kafka broker in its Testcontainers integration suite. Production Kafka factories, explicit error classification, parser/listener delegation, and the PostgreSQL outcome boundary are tested here; broker-level publish/consume/DLQ verification remains a deployment or CI smoke-test requirement.

## Non-Responsibilities

- Customer profile ownership.
- Authentication, authorization, sessions, or MFA.
- Transfer orchestration or payment execution.
- Public balance mutation APIs.
- Storing secrets or environment-specific configuration in the application image.

The balance mutation model is documented in [Account Balance And Events](docs/balance-and-events.md).

## Runtime Configuration

The service is a Spring Cloud Config client. It loads shared, service-specific, and environment-specific configuration from Config Server.

| Variable | Purpose | Default |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | Config Server base URL | `http://localhost:8888` |
| `SPRING_PROFILES_ACTIVE` | Runtime environment profile | Spring `default` profile |
| `ACCOUNT_LEDGER_KAFKA_SECURITY_PROTOCOL` | Kafka client transport protocol; production requires authenticated TLS | `SASL_SSL` |
| `ACCOUNT_LEDGER_KAFKA_ALLOW_INSECURE_TRANSPORT` | Explicit SIT-only opt-in for `PLAINTEXT`; never enable in production | `false` |
| `ACCOUNT_RESERVATION_EXPIRY_BATCH_SIZE` | Maximum due holds locked and expired per sweep | `100` |
| `ACCOUNT_RESERVATION_EXPIRY_SWEEP_DELAY_MS` | Delay between completed expiry sweeps | `30000` |

Secrets must not be committed to this repository or stored in the container image. Kubernetes and AWS environments will supply secrets through their approved secret-management integrations.

## Prerequisites

- Java 21.
- Network access to Maven Central for the initial dependency download.
- A running Config Server for normal application startup.
- Docker Desktop for image builds.
- Docker Desktop Kubernetes and Helm 4 for local SIT deployment.

A global Maven installation is not required because the Maven Wrapper is included.

```bash
java -version
./mvnw --version
docker version
kubectl config current-context
helm version --short
```

## Test And Quality Gate

Use these commands from the repository root:

```bash
./mvnw test
./mvnw spotless:apply
./mvnw verify
```

- `./mvnw test` runs the fast unit-test phase.
- `./mvnw spotless:apply` rewrites Java and repository text files to the enforced formatting.
- `./mvnw verify` is the full local quality gate. It runs formatting checks, unit tests, integration tests, and generates the JaCoCo coverage report under `target/site/jacoco/`.

Tests disable the external Config Server dependency so the build remains deterministic.

## Run From A Workstation For Debugging

SIT is the supported lowest runtime environment. A workstation JVM is only a temporary debugging process connected to forwarded SIT dependencies; it is not a separate `local` profile or deployment environment.

Follow the shared [workstation debugging procedure](https://github.com/digital-bank-java/.github/blob/main/docs/workstation-debugging-against-sit.md). It covers scaling this deployment to zero, forwarding Config Server and PostgreSQL, supplying temporary synthetic SIT credentials, and restoring the deployment after debugging.

After exporting the documented overrides, start Account Service:

```bash
./mvnw spring-boot:run
```

When Config Server provides the service port, the intended Account Service port is `8082`.

```bash
curl --fail http://localhost:8082/actuator/health
```

## Run With Docker

Build the image:

```bash
docker build \
  --tag digital-bank-java/account-service:0.0.3 \
  .
```

On Docker Desktop, connect the container to Config Server running on the host:

```bash
docker run --rm \
  --name digital-bank-java-account-service \
  --publish 8082:8082 \
  --env CONFIG_SERVER_URL=http://host.docker.internal:8888 \
  --env SPRING_PROFILES_ACTIVE=sit \
  digital-bank-java/account-service:0.0.3
```

Account Service is database-backed. When running it in Docker for debugging, also provide the temporary SIT datasource variables described in the shared workstation procedure, using `host.docker.internal` for the forwarded PostgreSQL host.

The runtime image uses numeric non-root user and group `10001:10001`.

## Deploy To Local SIT

The Config Server release must already be healthy in the `digital-bank-sit` namespace. The Account Service chart uses the internal Kubernetes address `http://config-server:8888` and activates the `sit` profile.

Validate the chart without changing the cluster:

```bash
helm lint helm --values helm/values-sit.yaml

helm template account-service helm --values helm/values-sit.yaml |
  kubectl apply --dry-run=client -f -
```

Install or upgrade the release:

```bash
helm upgrade --install account-service helm \
  --namespace digital-bank-sit \
  --create-namespace \
  --values helm/values-sit.yaml \
  --wait \
  --timeout 5m
```

Inspect the deployment:

```bash
helm status account-service --namespace digital-bank-sit
kubectl get deployment,pods,service --namespace digital-bank-sit
kubectl logs deployment/account-service --namespace digital-bank-sit
```

Temporarily forward the internal Service for workstation verification:

```bash
kubectl port-forward \
  service/account-service 18082:8082 \
  --namespace digital-bank-sit
```

From another terminal:

```bash
curl --fail http://localhost:18082/actuator/health
curl --fail http://localhost:18082/actuator/health/liveness
curl --fail http://localhost:18082/actuator/health/readiness
```

Stop port forwarding with `Ctrl+C`. Remove only this release when cleanup is required:

```bash
helm uninstall account-service --namespace digital-bank-sit
```

## API Gateway and Insomnia Verification

In local SIT, API clients should normally enter through API Gateway instead of port-forwarding Account Service directly.

Start API Gateway port forwarding:

```bash
kubectl port-forward \
  service/api-gateway 8080:8080 \
  --namespace digital-bank-sit
```

Use this Insomnia environment variable:

```json
{
  "apiGatewayUrl": "http://localhost:8080"
}
```

Verify Account Service through API Gateway:

```bash
curl --fail http://localhost:8080/account-service/actuator/health
curl --fail http://localhost:8080/admin/docs/account-service/v3/api-docs
```

In Insomnia, create equivalent requests using the environment variable:

```text
GET {{ _.apiGatewayUrl }}/account-service/actuator/health
GET {{ _.apiGatewayUrl }}/admin/docs/account-service/v3/api-docs
POST {{ _.apiGatewayUrl }}/api/v1/accounts
GET {{ _.apiGatewayUrl }}/api/v1/accounts/{{ _.accountId }}
GET {{ _.apiGatewayUrl }}/api/v1/customers/{{ _.customerId }}/accounts
GET {{ _.apiGatewayUrl }}/admin/v1/accounts?page=0&size=20
GET {{ _.apiGatewayUrl }}/admin/v1/accounts?customerId={{ _.customerId }}&status=ACTIVE&page=0&size=20&sort=balance,desc&sort=createdAt,asc
```

Use this request body when opening an account:

```json
{
  "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "accountNumber": "ACC-000000000001",
  "iban": "AE0703312345678900000001",
  "accountType": "CURRENT",
  "currency": "AED",
  "openingRequestId": "open-account-request-001"
}
```

The equivalent terminal command is:

```bash
curl --request POST http://localhost:8080/api/v1/accounts \
  --header "Content-Type: application/json" \
  --data '{
    "customerId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "accountNumber": "ACC-000000000001",
    "iban": "AE0703312345678900000001",
    "accountType": "CURRENT",
    "currency": "AED",
    "openingRequestId": "open-account-request-001"
  }'
```

Copy the returned `accountId`, then verify lookup endpoints:

```bash
curl --fail http://localhost:8080/api/v1/accounts/<account-id>
curl --fail http://localhost:8080/api/v1/customers/3fa85f64-5717-4562-b3fc-2c963f66afa6/accounts
curl --fail "http://localhost:8080/admin/v1/accounts?customerId=3fa85f64-5717-4562-b3fc-2c963f66afa6&status=ACTIVE&page=0&size=20&sort=balance,desc&sort=createdAt,asc"
```

Stop API Gateway port forwarding with `Ctrl+C`.

## Deployment Security

The Kubernetes deployment:

- Runs as numeric non-root user and group `10001`.
- Disables privilege escalation and drops Linux capabilities.
- Uses a read-only root filesystem with bounded temporary storage.
- Does not mount the default Kubernetes service account token.
- Exposes the application only through an internal `ClusterIP` Service.
- Defines startup, liveness, and readiness probes.

## CI Validation

Pull requests and changes to `main` run independent jobs that:

- Execute Maven verification with Java 21.
- Lint and render the Helm chart with Helm 4.2.0.
- Build the container image, verify its non-root user, and smoke-test its health endpoint.

Third-party GitHub Actions are pinned to immutable commit SHAs.

## Environment Promotion

The same application artifact is intended to move through SIT, UAT, and PROD without being rebuilt. Deployment pipelines provide environment-specific immutable image tags, Config Server addresses, profiles, resource sizing, and infrastructure integrations.

AWS deployment will map the Kubernetes workload to EKS and use managed AWS services for configuration credentials, networking, observability, and secrets. Environment-specific secrets remain outside Git and Helm values.

## Development Workflow

Changes must be made on a dedicated branch and merged through a pull request. Do not commit directly to `main`.

Before opening a pull request:

```bash
git status
./mvnw test
helm lint helm --strict
git diff --check
```
