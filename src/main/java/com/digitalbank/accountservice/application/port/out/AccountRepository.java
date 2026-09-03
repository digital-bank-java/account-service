package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.CustomerId;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Account save(Account account);

    Optional<Account> findById(AccountId accountId);

    List<Account> findByCustomerId(CustomerId customerId);

    AccountSearchResult search(AccountSearchCriteria criteria);
}
