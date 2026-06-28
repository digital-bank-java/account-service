package com.digitalbank.accountservice.application.port.in;

import java.util.List;

public record PaginatedAccountProfiles(
		List<AccountProfile> items,
		String nextPageToken,
		int pageSize) {
}
