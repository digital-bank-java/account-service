package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.CustomerId;
import java.util.List;

public interface ListCustomerAccountsInputPort {

    List<AccountProfile> listCustomerAccounts(CustomerId customerId);
}
