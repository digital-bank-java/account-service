package com.digitalbank.accountservice.adapter.in.web;

import java.util.List;

import com.digitalbank.accountservice.application.port.in.PaginatedAccountProfiles;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Paginated account search result")
record AccountPageResponse(
		@Schema(description = "Returned account page")
		List<AccountResponse> items,

		@Schema(description = "Token for the next page. Omitted when there are no more results.")
		String nextPageToken,

		@Schema(description = "Effective page size")
		int pageSize) {

	static AccountPageResponse from(PaginatedAccountProfiles page) {
		return new AccountPageResponse(
				page.items().stream()
						.map(AccountResponse::from)
						.toList(),
				page.nextPageToken(),
				page.pageSize());
	}
}
