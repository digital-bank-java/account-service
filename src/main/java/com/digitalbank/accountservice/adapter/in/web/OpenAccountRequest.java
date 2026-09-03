package com.digitalbank.accountservice.adapter.in.web;

import com.digitalbank.accountservice.domain.model.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotBlank @Size(max = 34) String accountNumber,
        @Size(max = 34) String iban,
        @NotNull AccountType accountType,

        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter uppercase ISO currency code")
        String currency,

        @NotBlank @Size(max = 100) String openingRequestId) {}
