package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

public record OpenAccountCommand(
		CustomerId customerId,
		String accountNumber,
		String iban,
		AccountType accountType,
		String currency,
		String openingRequestId) {
}
