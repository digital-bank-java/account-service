package com.digitalbank.accountservice.adapter.in.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
final class AccountResourceAuthorization {

    private static final String ADMIN_SCOPE = "SCOPE_admin.internal";
    private static final String SELF_SCOPE = "SCOPE_account.self";

    void requireCustomerAccess(UUID customerId, Authentication authentication) {
        requireOwnedResource(customerId, authentication, "customer");
    }

    void requireAccountAccess(UUID accountId, UUID customerId, Authentication authentication) {
        requireOwnedResource(customerId, authentication, "account " + accountId);
    }

    private static void requireOwnedResource(UUID customerId, Authentication authentication, String resource) {
        if (hasAuthority(authentication, ADMIN_SCOPE)) {
            return;
        }
        if (!hasAuthority(authentication, SELF_SCOPE)
                || authentication == null
                || !customerId.toString().equals(authentication.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "The authenticated subject cannot access this " + resource);
        }
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch(authority::equals);
    }
}
