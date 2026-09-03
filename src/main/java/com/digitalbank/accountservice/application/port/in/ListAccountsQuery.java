package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.application.model.AccountSortOrder;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import java.util.List;

public record ListAccountsQuery(
        CustomerId customerId,
        AccountStatus status,
        AccountType accountType,
        String currency,
        int pageNumber,
        int pageSize,
        List<AccountSortOrder> sort) {}
