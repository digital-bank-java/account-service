package com.digitalbank.accountservice.application.port.out;

import java.util.List;

import com.digitalbank.accountservice.domain.model.Account;

public record AccountSearchResult(
		List<Account> accounts,
		String nextPageToken,
		int pageSize) {
}
