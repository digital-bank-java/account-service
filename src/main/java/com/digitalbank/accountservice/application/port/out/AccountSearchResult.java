package com.digitalbank.accountservice.application.port.out;

import java.util.List;

import com.digitalbank.accountservice.domain.model.Account;

public record AccountSearchResult(
		List<Account> accounts,
		int pageNumber,
		int pageSize,
		long totalElements,
		int totalPages,
		boolean last) {
}
