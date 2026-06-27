package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountApiIntegrationTests {

	private static final AtomicInteger ACCOUNT_SEQUENCE = new AtomicInteger(1);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

	private final HttpClient httpClient = HttpClient.newHttpClient();
	private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

	@LocalServerPort
	private int port;

	@Test
	void opensAndRetrievesAccount() throws Exception {
		var customerId = UUID.randomUUID();
		var openResponse = sendJson("POST", "/api/v1/accounts", openAccountRequest(customerId, "open-account-request-001"));

		assertThat(openResponse.statusCode()).isEqualTo(201);
		assertThat(openResponse.headers().firstValue("location")).hasValueSatisfying(location -> {
			assertThat(location).startsWith("/api/v1/accounts/");
		});

		var openedAccount = objectMapper.readTree(openResponse.body());
		assertThat(openedAccount.path("accountId").asText()).isNotBlank();
		assertThat(openedAccount.path("customerId").asText()).isEqualTo(customerId.toString());
		assertThat(openedAccount.path("accountType").asText()).isEqualTo("CURRENT");
		assertThat(openedAccount.path("currency").asText()).isEqualTo("AED");
		assertThat(openedAccount.path("status").asText()).isEqualTo("ACTIVE");
		assertThat(openedAccount.path("currentBalance").decimalValue()).isEqualByComparingTo("0");
		assertThat(openedAccount.path("availableBalance").decimalValue()).isEqualByComparingTo("0");

		var getResponse = send("GET", "/api/v1/accounts/" + openedAccount.path("accountId").asText());

		assertThat(getResponse.statusCode()).isEqualTo(200);
		var retrievedAccount = objectMapper.readTree(getResponse.body());
		assertThat(retrievedAccount.path("accountId").asText()).isEqualTo(openedAccount.path("accountId").asText());
		assertThat(retrievedAccount.path("customerId").asText()).isEqualTo(customerId.toString());
	}

	@Test
	void listsCustomerAccounts() throws Exception {
		var customerId = UUID.randomUUID();
		sendJson("POST", "/api/v1/accounts", openAccountRequest(customerId, "open-account-request-002"));
		sendJson("POST", "/api/v1/accounts", openAccountRequest(customerId, "open-account-request-003"));

		var response = send("GET", "/api/v1/customers/" + customerId + "/accounts");

		assertThat(response.statusCode()).isEqualTo(200);
		var accounts = objectMapper.readTree(response.body());
		assertThat(accounts).hasSize(2);
		assertThat(accounts.findValuesAsText("customerId")).containsOnly(customerId.toString());
	}

	@Test
	void rejectsInvalidOpenAccountRequest() throws Exception {
		var response = sendJson("POST", "/api/v1/accounts", """
				{
				  "customerId": null,
				  "accountNumber": "",
				  "accountType": null,
				  "currency": "aed",
				  "openingRequestId": ""
				}
				""");

		assertThat(response.statusCode()).isEqualTo(400);
		var problem = objectMapper.readTree(response.body());
		assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
		assertThat(problem.path("errors")).isNotEmpty();
	}

	@Test
	void returnsProblemWhenAccountDoesNotExist() throws Exception {
		var missingAccountId = UUID.randomUUID();

		var response = send("GET", "/api/v1/accounts/" + missingAccountId);

		assertThat(response.statusCode()).isEqualTo(404);
		var problem = objectMapper.readTree(response.body());
		assertThat(problem.path("title").asText()).isEqualTo("Account not found");
		assertThat(problem.path("accountId").asText()).isEqualTo(missingAccountId.toString());
	}

	@Test
	void rejectsDuplicateAccountRequest() throws Exception {
		var customerId = UUID.randomUUID();
		var request = openAccountRequest(customerId, "duplicate-open-account-request");

		var firstResponse = sendJson("POST", "/api/v1/accounts", request);
		var duplicateResponse = sendJson("POST", "/api/v1/accounts", request);

		assertThat(firstResponse.statusCode()).isEqualTo(201);
		assertThat(duplicateResponse.statusCode()).isEqualTo(409);
		var problem = objectMapper.readTree(duplicateResponse.body());
		assertThat(problem.path("title").asText()).isEqualTo("Account conflict");
	}

	@Test
	void publishesOpenApiContract() throws Exception {
		var response = send("GET", "/v3/api-docs");

		assertThat(response.statusCode()).isEqualTo(200);
		var openApi = objectMapper.readTree(response.body());
		assertThat(openApi.path("paths").has("/api/v1/accounts")).isTrue();
		assertThat(openApi.path("paths").has("/api/v1/accounts/{accountId}")).isTrue();
		assertThat(openApi.path("paths").has("/api/v1/customers/{customerId}/accounts")).isTrue();
	}

	private static String openAccountRequest(UUID customerId, String openingRequestId) {
		var sequence = ACCOUNT_SEQUENCE.getAndIncrement();
		return """
				{
				  "customerId": "%s",
				  "accountNumber": "ACC-%012d",
				  "iban": "AE07033123456789%08d",
				  "accountType": "CURRENT",
				  "currency": "AED",
				  "openingRequestId": "%s"
				}
				""".formatted(customerId, sequence, sequence, openingRequestId);
	}

	private HttpResponse<String> send(String method, String path) throws Exception {
		var request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.method(method, HttpRequest.BodyPublishers.noBody())
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private HttpResponse<String> sendJson(String method, String path, String body) throws Exception {
		var request = HttpRequest.newBuilder()
				.uri(URI.create("http://localhost:" + port + path))
				.header("Content-Type", "application/json")
				.method(method, HttpRequest.BodyPublishers.ofString(body))
				.build();
		return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}
}
