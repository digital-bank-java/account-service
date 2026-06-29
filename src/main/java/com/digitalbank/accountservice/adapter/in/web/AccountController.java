package com.digitalbank.accountservice.adapter.in.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import com.digitalbank.accountservice.application.model.AccountSortOrder;
import com.digitalbank.accountservice.application.port.in.GetAccountInputPort;
import com.digitalbank.accountservice.application.port.in.ListAccountsInputPort;
import com.digitalbank.accountservice.application.port.in.ListAccountsQuery;
import com.digitalbank.accountservice.application.port.in.ListCustomerAccountsInputPort;
import com.digitalbank.accountservice.application.port.in.OpenAccountCommand;
import com.digitalbank.accountservice.application.port.in.OpenAccountInputPort;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Accounts")
@Validated
class AccountController {

	private final OpenAccountInputPort openAccountInputPort;
	private final GetAccountInputPort getAccountInputPort;
	private final ListCustomerAccountsInputPort listCustomerAccountsInputPort;
	private final ListAccountsInputPort listAccountsInputPort;

	AccountController(
			OpenAccountInputPort openAccountInputPort,
			GetAccountInputPort getAccountInputPort,
			ListCustomerAccountsInputPort listCustomerAccountsInputPort,
			ListAccountsInputPort listAccountsInputPort) {
		this.openAccountInputPort = openAccountInputPort;
		this.getAccountInputPort = getAccountInputPort;
		this.listCustomerAccountsInputPort = listCustomerAccountsInputPort;
		this.listAccountsInputPort = listAccountsInputPort;
	}

	@PostMapping("/api/v1/accounts")
	@Operation(summary = "Open an account")
	@ApiResponse(responseCode = "201", description = "Account opened", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AccountResponse.class)))
	@ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class),
			examples = @ExampleObject(
					name = "validation-error",
					summary = "Validation failure",
					value = VALIDATION_PROBLEM_EXAMPLE)))
	@ApiResponse(responseCode = "409", description = "Account already exists", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class),
			examples = @ExampleObject(
					name = "account-conflict",
					summary = "Duplicate account request",
					value = ACCOUNT_CONFLICT_PROBLEM_EXAMPLE)))
	ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
		var profile = openAccountInputPort.openAccount(new OpenAccountCommand(
				new CustomerId(request.customerId()),
				request.accountNumber(),
				request.iban(),
				request.accountType(),
				request.currency(),
				request.openingRequestId()));
		var response = AccountResponse.from(profile);

		return ResponseEntity
				.status(HttpStatus.CREATED)
				.location(URI.create("/api/v1/accounts/" + response.accountId()))
				.body(response);
	}

	@GetMapping("/api/v1/accounts/{accountId}")
	@Operation(summary = "Get an account")
	@ApiResponse(responseCode = "200", description = "Account returned", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AccountResponse.class)))
	@ApiResponse(responseCode = "404", description = "Account not found", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class),
			examples = @ExampleObject(
					name = "account-not-found",
					summary = "Account not found",
					value = ACCOUNT_NOT_FOUND_PROBLEM_EXAMPLE)))
	ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
		var profile = getAccountInputPort.getAccount(new AccountId(accountId));
		return ResponseEntity.ok(AccountResponse.from(profile));
	}

	@GetMapping("/api/v1/customers/{customerId}/accounts")
	@Operation(summary = "List customer accounts")
	@ApiResponse(responseCode = "200", description = "Customer accounts returned", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			array = @ArraySchema(schema = @Schema(implementation = AccountResponse.class))))
	ResponseEntity<List<AccountResponse>> listCustomerAccounts(@PathVariable UUID customerId) {
		var accounts = listCustomerAccountsInputPort.listCustomerAccounts(new CustomerId(customerId)).stream()
				.map(AccountResponse::from)
				.toList();
		return ResponseEntity.ok(accounts);
	}

	@GetMapping("/admin/v1/accounts")
	@Operation(summary = "Search accounts for administration")
	@ApiResponse(responseCode = "200", description = "Accounts returned", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AccountPageResponse.class)))
	@ApiResponse(responseCode = "400", description = "Invalid query parameter", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class),
			examples = @ExampleObject(
					name = "validation-error",
					summary = "Query parameter validation failure",
					value = VALIDATION_PROBLEM_EXAMPLE)))
	ResponseEntity<AccountPageResponse> listAccounts(
			@RequestParam(required = false) UUID customerId,
			@RequestParam(required = false) AccountStatus status,
			@RequestParam(required = false) AccountType accountType,
			@RequestParam(required = false) @Pattern(regexp = "^[A-Z]{3}$") String currency,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@RequestParam(required = false) List<String> sort) {
		var query = new ListAccountsQuery(
				customerId == null ? null : new CustomerId(customerId),
				status,
				accountType,
				currency,
				page,
				size,
				parseSort(sort));
		return ResponseEntity.ok(AccountPageResponse.from(listAccountsInputPort.listAccounts(query)));
	}

	private static List<AccountSortOrder> parseSort(List<String> sort) {
		if (sort == null || sort.isEmpty()) {
			return List.of();
		}

		var tokens = sort.stream()
				.flatMap(value -> List.of(value.split(",", -1)).stream())
				.map(String::trim)
				.filter(token -> !token.isEmpty())
				.toList();

		var orders = new ArrayList<AccountSortOrder>();
		for (var index = 0; index < tokens.size(); index++) {
			var propertyToken = tokens.get(index);
			var property = SORT_PROPERTIES.get(propertyToken);
			if (property == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort property: " + propertyToken);
			}

			var direction = AccountSortOrder.Direction.ASC;
			if (index + 1 < tokens.size()) {
				var nextToken = tokens.get(index + 1);
				if (isSortDirection(nextToken)) {
					direction = parseSortDirection(nextToken);
					index++;
				}
				else if (!SORT_PROPERTIES.containsKey(nextToken)) {
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction: " + nextToken);
				}
			}

			orders.add(new AccountSortOrder(property, direction));
		}

		return orders;
	}

	private static boolean isSortDirection(String direction) {
		var normalizedDirection = direction.toLowerCase(Locale.ROOT);
		return "asc".equals(normalizedDirection) || "desc".equals(normalizedDirection);
	}

	private static AccountSortOrder.Direction parseSortDirection(String direction) {
		return switch (direction.toLowerCase(Locale.ROOT)) {
			case "asc" -> AccountSortOrder.Direction.ASC;
			case "desc" -> AccountSortOrder.Direction.DESC;
			default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort direction: " + direction);
		};
	}

	private static final Map<String, String> SORT_PROPERTIES = Map.ofEntries(
			Map.entry("accountId", "id"),
			Map.entry("customerId", "customerId"),
			Map.entry("accountNumber", "accountNumber"),
			Map.entry("accountType", "accountType"),
			Map.entry("currency", "currency"),
			Map.entry("status", "status"),
			Map.entry("balance", "currentBalance"),
			Map.entry("currentBalance", "currentBalance"),
			Map.entry("availableBalance", "availableBalance"),
			Map.entry("createdAt", "createdAt"),
			Map.entry("updatedAt", "updatedAt"));

	private static final String VALIDATION_PROBLEM_EXAMPLE = """
			{
			  "type": "about:blank",
			  "title": "Invalid request",
			  "status": 400,
			  "detail": "Request validation failed",
			  "errors": [
			    {
			      "field": "currency",
			      "message": "must match ISO 4217 uppercase format"
			    }
			  ]
			}
			""";

	private static final String ACCOUNT_CONFLICT_PROBLEM_EXAMPLE = """
			{
			  "type": "about:blank",
			  "title": "Account conflict",
			  "status": 409,
			  "detail": "Account opening request already exists",
			  "openingRequestId": "open-account-request-001"
			}
			""";

	private static final String ACCOUNT_NOT_FOUND_PROBLEM_EXAMPLE = """
			{
			  "type": "about:blank",
			  "title": "Account not found",
			  "status": 404,
			  "detail": "Account was not found",
			  "accountId": "3fa85f64-5717-4562-b3fc-2c963f66afa6"
			}
			""";
}
