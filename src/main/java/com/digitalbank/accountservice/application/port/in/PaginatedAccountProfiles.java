package com.digitalbank.accountservice.application.port.in;

import java.util.List;

public record PaginatedAccountProfiles(
        List<AccountProfile> items, int pageNumber, int pageSize, long totalElements, int totalPages, boolean last) {}
