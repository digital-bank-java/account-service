package com.digitalbank.accountservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountApiIT {

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
        var openResponse =
                sendJson("POST", "/api/v1/accounts", openAccountRequest(customerId, "open-account-request-001"));

        assertThat(openResponse.statusCode()).isEqualTo(201);
        assertContentType(openResponse, "application/json");
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

        var getResponse = send(
                "GET", "/api/v1/accounts/" + openedAccount.path("accountId").asText());

        assertThat(getResponse.statusCode()).isEqualTo(200);
        assertContentType(getResponse, "application/json");
        var retrievedAccount = objectMapper.readTree(getResponse.body());
        assertThat(retrievedAccount.path("accountId").asText())
                .isEqualTo(openedAccount.path("accountId").asText());
        assertThat(retrievedAccount.path("customerId").asText()).isEqualTo(customerId.toString());
    }

    @Test
    void listsMultipleAccountsForSameCustomerTypeAndCurrency() throws Exception {
        var customerId = UUID.randomUUID();
        var firstOpenResponse = sendJson(
                "POST",
                "/api/v1/accounts",
                openAccountRequest(customerId, "open-account-request-002", "CURRENT", "AED"));
        var secondOpenResponse = sendJson(
                "POST",
                "/api/v1/accounts",
                openAccountRequest(customerId, "open-account-request-003", "CURRENT", "AED"));
        var firstAccount = objectMapper.readTree(firstOpenResponse.body());
        var secondAccount = objectMapper.readTree(secondOpenResponse.body());

        var response = send("GET", "/api/v1/customers/" + customerId + "/accounts");

        assertThat(firstOpenResponse.statusCode()).isEqualTo(201);
        assertThat(secondOpenResponse.statusCode()).isEqualTo(201);
        assertThat(response.statusCode()).isEqualTo(200);
        assertContentType(response, "application/json");
        var accounts = objectMapper.readTree(response.body());
        assertThat(accounts).hasSize(2);
        assertThat(accounts.findValuesAsText("accountId"))
                .containsExactlyInAnyOrder(
                        firstAccount.path("accountId").asText(),
                        secondAccount.path("accountId").asText());
        assertThat(accounts.findValuesAsText("customerId")).containsOnly(customerId.toString());
        assertThat(accounts.findValuesAsText("accountType")).containsOnly("CURRENT");
        assertThat(accounts.findValuesAsText("currency")).containsOnly("AED");
    }

    @Test
    void returnsEmptyAdminAccountPage() throws Exception {
        var response = send("GET", "/admin/v1/accounts?customerId=" + UUID.randomUUID());

        assertThat(response.statusCode()).isEqualTo(200);
        assertContentType(response, "application/json");
        var page = objectMapper.readTree(response.body());
        assertThat(page.path("items")).isEmpty();
        assertThat(page.has("nextPageToken")).isFalse();
        assertThat(page.path("pageNumber").asInt()).isZero();
        assertThat(page.path("pageSize").asInt()).isEqualTo(20);
        assertThat(page.path("totalElements").asLong()).isZero();
        assertThat(page.path("totalPages").asInt()).isZero();
        assertThat(page.path("last").asBoolean()).isTrue();
    }

    @Test
    void returnsFilteredAdminAccountPageWithTotals() throws Exception {
        var customerId = UUID.randomUUID();
        var otherCustomerId = UUID.randomUUID();
        var firstOpenResponse = sendJson(
                "POST",
                "/api/v1/accounts",
                openAccountRequest(customerId, "admin-open-account-request-001", "CURRENT", "AED"));
        var secondOpenResponse = sendJson(
                "POST",
                "/api/v1/accounts",
                openAccountRequest(customerId, "admin-open-account-request-002", "SAVINGS", "AED"));
        sendJson(
                "POST",
                "/api/v1/accounts",
                openAccountRequest(otherCustomerId, "admin-open-account-request-003", "CURRENT", "USD"));
        var firstAccount = objectMapper.readTree(firstOpenResponse.body());
        var secondAccount = objectMapper.readTree(secondOpenResponse.body());

        var firstPageResponse = send(
                "GET",
                "/admin/v1/accounts?customerId=" + customerId + "&currency=AED&page=0&size=1"
                        + "&sort=balance,desc&sort=createdAt,asc");

        assertThat(firstPageResponse.statusCode()).isEqualTo(200);
        assertContentType(firstPageResponse, "application/json");
        var firstPage = objectMapper.readTree(firstPageResponse.body());
        assertThat(firstPage.path("items")).hasSize(1);
        assertThat(firstPage.path("items").findValuesAsText("customerId")).containsOnly(customerId.toString());
        assertThat(firstPage.path("items").findValuesAsText("currency")).containsOnly("AED");
        assertThat(firstPage.has("nextPageToken")).isFalse();
        assertThat(firstPage.path("pageNumber").asInt()).isZero();
        assertThat(firstPage.path("pageSize").asInt()).isEqualTo(1);
        assertThat(firstPage.path("totalElements").asLong()).isEqualTo(2);
        assertThat(firstPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.path("last").asBoolean()).isFalse();
        var firstPageAccountId =
                firstPage.path("items").get(0).path("accountId").asText();

        var secondPageResponse = send(
                "GET",
                "/admin/v1/accounts?customerId=" + customerId + "&currency=AED&page=1&size=1"
                        + "&sort=balance,desc&sort=createdAt,asc");

        assertThat(secondPageResponse.statusCode()).isEqualTo(200);
        var secondPage = objectMapper.readTree(secondPageResponse.body());
        assertThat(secondPage.path("items")).hasSize(1);
        assertThat(secondPage.has("nextPageToken")).isFalse();
        assertThat(secondPage.path("pageNumber").asInt()).isEqualTo(1);
        assertThat(secondPage.path("pageSize").asInt()).isEqualTo(1);
        assertThat(secondPage.path("totalElements").asLong()).isEqualTo(2);
        assertThat(secondPage.path("totalPages").asInt()).isEqualTo(2);
        assertThat(secondPage.path("last").asBoolean()).isTrue();
        var secondPageAccountId =
                secondPage.path("items").get(0).path("accountId").asText();
        assertThat(firstPageAccountId).isNotEqualTo(secondPageAccountId);
        assertThat(List.of(firstPageAccountId, secondPageAccountId))
                .containsExactlyInAnyOrder(
                        firstAccount.path("accountId").asText(),
                        secondAccount.path("accountId").asText());
    }

    @Test
    void rejectsInvalidAdminAccountPageSize() throws Exception {
        var response = send("GET", "/admin/v1/accounts?size=101");

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
    }

    @Test
    void rejectsUnsupportedAdminAccountSortField() throws Exception {
        var response = send("GET", "/admin/v1/accounts?sort=unsupportedField,asc");

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
    }

    @Test
    void returnsAdminAccountPageWithSingleDescendingSortParameter() throws Exception {
        var response = send("GET", "/admin/v1/accounts?status=ACTIVE&page=0&size=20&sort=createdAt,desc");

        assertThat(response.statusCode()).isEqualTo(200);
        assertContentType(response, "application/json");
        var page = objectMapper.readTree(response.body());
        assertThat(page.path("pageNumber").asInt()).isZero();
        assertThat(page.path("pageSize").asInt()).isEqualTo(20);
    }

    @Test
    void rejectsUnsupportedAdminAccountSortDirection() throws Exception {
        var response = send("GET", "/admin/v1/accounts?sort=createdAt,sideways");

        assertThat(response.statusCode()).isEqualTo(400);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
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
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/validation-error");
        assertThat(problem.path("title").asText()).isEqualTo("Invalid request");
        assertThat(problem.path("errors")).isNotEmpty();
    }

    @Test
    void returnsProblemWhenAccountDoesNotExist() throws Exception {
        var missingAccountId = UUID.randomUUID();

        var response = send("GET", "/api/v1/accounts/" + missingAccountId);

        assertThat(response.statusCode()).isEqualTo(404);
        assertContentType(response, "application/problem+json");
        var problem = objectMapper.readTree(response.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/account-not-found");
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
        assertContentType(duplicateResponse, "application/problem+json");
        var problem = objectMapper.readTree(duplicateResponse.body());
        assertThat(problem.path("type").asText())
                .isEqualTo("https://digital-bank-java.local/problems/account-conflict");
        assertThat(problem.path("title").asText()).isEqualTo("Account conflict");
    }

    @Test
    void publishesOpenApiContract() throws Exception {
        var response = send("GET", "/v3/api-docs");

        assertThat(response.statusCode()).isEqualTo(200);
        assertContentType(response, "application/json");
        var openApi = objectMapper.readTree(response.body());
        assertThat(openApi.path("info").path("title").asText()).isEqualTo("Digital Bank Account Service API");
        assertThat(openApi.path("info").path("description").asText())
                .isEqualTo("Account lifecycle and account lookup APIs for the Digital Bank Java platform.");
        assertThat(openApi.path("info").path("version").asText()).isEqualTo("1.0.0");
        assertThat(openApi.path("paths").has("/api/v1/accounts")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/accounts/{accountId}")).isTrue();
        assertThat(openApi.path("paths").has("/api/v1/customers/{customerId}/accounts"))
                .isTrue();
        assertThat(openApi.path("paths").has("/admin/v1/accounts")).isTrue();

        var openAccountResponses =
                openApi.path("paths").path("/api/v1/accounts").path("post").path("responses");
        assertThat(openAccountResponses.path("201").path("content").has("application/json"))
                .isTrue();
        assertThat(openAccountResponses.path("400").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(openAccountResponses.path("409").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(openAccountResponses.path("400").path("content").has("application/json"))
                .isFalse();
        assertThat(openAccountResponses
                        .path("400")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .has("validation-error"))
                .isTrue();
        assertThat(openAccountResponses
                        .path("409")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .has("account-conflict"))
                .isTrue();

        var getAccountResponses = openApi.path("paths")
                .path("/api/v1/accounts/{accountId}")
                .path("get")
                .path("responses");
        assertThat(getAccountResponses.path("200").path("content").has("application/json"))
                .isTrue();
        assertThat(getAccountResponses.path("404").path("content").has("application/problem+json"))
                .isTrue();
        assertThat(getAccountResponses
                        .path("404")
                        .path("content")
                        .path("application/problem+json")
                        .path("examples")
                        .has("account-not-found"))
                .isTrue();

        var listCustomerAccountsResponses = openApi.path("paths")
                .path("/api/v1/customers/{customerId}/accounts")
                .path("get")
                .path("responses");
        assertThat(listCustomerAccountsResponses.path("200").path("content").has("application/json"))
                .isTrue();

        var adminAccountResponses =
                openApi.path("paths").path("/admin/v1/accounts").path("get").path("responses");
        assertThat(adminAccountResponses.path("200").path("content").has("application/json"))
                .isTrue();
        assertThat(adminAccountResponses.path("400").path("content").has("application/problem+json"))
                .isTrue();
    }

    private static void assertContentType(HttpResponse<String> response, String expectedContentType) {
        assertThat(response.headers().firstValue("content-type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith(expectedContentType));
    }

    private static String openAccountRequest(UUID customerId, String openingRequestId) {
        return openAccountRequest(customerId, openingRequestId, "CURRENT", "AED");
    }

    private static String openAccountRequest(
            UUID customerId, String openingRequestId, String accountType, String currency) {
        var sequence = ACCOUNT_SEQUENCE.getAndIncrement();
        return """
				{
				  "customerId": "%s",
				  "accountNumber": "ACC-%012d",
				  "iban": "AE07033123456789%08d",
				  "accountType": "%s",
				  "currency": "%s",
				  "openingRequestId": "%s"
				}
				""".formatted(customerId, sequence, sequence, accountType, currency, openingRequestId);
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
