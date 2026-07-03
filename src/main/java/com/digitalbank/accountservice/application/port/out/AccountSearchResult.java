package com.digitalbank.accountservice.application.port.out;

import com.digitalbank.accountservice.domain.model.Account;
import java.util.List;

public record AccountSearchResult(
        List<Account> accounts, int pageNumber, int pageSize, long totalElements, int totalPages, boolean last) {}
