package com.digitalbank.accountservice.application.port.in;

import com.digitalbank.accountservice.domain.model.AccountId;

public interface GetAccountInputPort {

	AccountProfile getAccount(AccountId accountId);
}
