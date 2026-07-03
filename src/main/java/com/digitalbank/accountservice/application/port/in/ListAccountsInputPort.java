package com.digitalbank.accountservice.application.port.in;

public interface ListAccountsInputPort {

    PaginatedAccountProfiles listAccounts(ListAccountsQuery query);
}
