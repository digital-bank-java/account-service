package com.digitalbank.accountservice.application.port.in;

import java.util.List;

import com.digitalbank.accountservice.application.model.AccountSortOrder;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;

public record ListAccountsQuery(
		CustomerId customerId,
		AccountStatus status,
		AccountType accountType,
		String currency,
		int pageNumber,
		int pageSize,
		List<AccountSortOrder> sort) {
}
