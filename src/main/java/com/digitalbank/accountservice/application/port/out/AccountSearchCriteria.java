package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

public record AccountSearchCriteria(
		CustomerId customerId,
		AccountStatus status,
		AccountType accountType,
		String currency,
		int pageSize,
		int pageNumber) {
}
