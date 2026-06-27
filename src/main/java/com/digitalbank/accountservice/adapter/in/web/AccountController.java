package com.digitalbank.accountservice.adapter.in.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.digitalbank.accountservice.application.port.in.GetAccountInputPort;
import com.digitalbank.accountservice.application.port.in.ListCustomerAccountsInputPort;
import com.digitalbank.accountservice.application.port.in.OpenAccountCommand;
import com.digitalbank.accountservice.application.port.in.OpenAccountInputPort;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.CustomerId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts")
class AccountController {

	private final OpenAccountInputPort openAccountInputPort;
	private final GetAccountInputPort getAccountInputPort;
	private final ListCustomerAccountsInputPort listCustomerAccountsInputPort;

	AccountController(
			OpenAccountInputPort openAccountInputPort,
			GetAccountInputPort getAccountInputPort,
			ListCustomerAccountsInputPort listCustomerAccountsInputPort) {
		this.openAccountInputPort = openAccountInputPort;
		this.getAccountInputPort = getAccountInputPort;
		this.listCustomerAccountsInputPort = listCustomerAccountsInputPort;
	}

	@PostMapping("/accounts")
	@Operation(summary = "Open an account")
	@ApiResponse(responseCode = "201", description = "Account opened", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AccountResponse.class)))
	@ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Account already exists", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class)))
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

	@GetMapping("/accounts/{accountId}")
	@Operation(summary = "Get an account")
	@ApiResponse(responseCode = "200", description = "Account returned", content = @Content(
			mediaType = MediaType.APPLICATION_JSON_VALUE,
			schema = @Schema(implementation = AccountResponse.class)))
	@ApiResponse(responseCode = "404", description = "Account not found", content = @Content(
			mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
			schema = @Schema(implementation = ProblemDetail.class)))
	ResponseEntity<AccountResponse> getAccount(@PathVariable UUID accountId) {
		var profile = getAccountInputPort.getAccount(new AccountId(accountId));
		return ResponseEntity.ok(AccountResponse.from(profile));
	}

	@GetMapping("/customers/{customerId}/accounts")
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
}
