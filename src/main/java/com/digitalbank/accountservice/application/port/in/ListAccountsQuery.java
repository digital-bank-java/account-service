package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

public record ListAccountsQuery(
		CustomerId customerId,
		AccountStatus status,
		AccountType accountType,
		String currency,
		int pageSize,
		String pageToken) {
}
