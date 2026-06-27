package com.digitalbank.accountservice.application.port.in;

import java.util.List;

import com.digitalbank.accountservice.domain.model.CustomerId;

public interface ListCustomerAccountsInputPort {

	List<AccountProfile> listCustomerAccounts(CustomerId customerId);
}
