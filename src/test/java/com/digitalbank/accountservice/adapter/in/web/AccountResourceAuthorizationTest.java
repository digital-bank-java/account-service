package com.digitalbank.accountservice.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

class AccountResourceAuthorizationTest {

    private final AccountResourceAuthorization authorization = new AccountResourceAuthorization();
    private final UUID customerId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void allowsAdminIdentityToAccessAnyAccount() {
        var authentication = authentication("transfer-orchestrator", "SCOPE_admin.internal");

        authorization.requireCustomerAccess(customerId, authentication);
        authorization.requireAccountAccess(accountId, customerId, authentication);
    }

    @Test
    void allowsAccountOwnerWithSelfScope() {
        var authentication = authentication(customerId.toString(), "SCOPE_account.self");

        authorization.requireCustomerAccess(customerId, authentication);
        authorization.requireAccountAccess(accountId, customerId, authentication);
    }

    @Test
    void rejectsAuthenticatedIdentityWithoutOwnershipScope() {
        var authentication = authentication("someone-else", "SCOPE_profile.read");

        assertThatThrownBy(() -> authorization.requireCustomerAccess(customerId, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception ->
                        ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
    }

    @Test
    void rejectsAccountOwnerForAnotherAccountOwner() {
        var authentication = authentication(UUID.randomUUID().toString(), "SCOPE_account.self");

        assertThatThrownBy(() -> authorization.requireAccountAccess(accountId, customerId, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception ->
                        ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
    }

    private static UsernamePasswordAuthenticationToken authentication(String subject, String scope) {
        return new UsernamePasswordAuthenticationToken(subject, "n/a", List.of(new SimpleGrantedAuthority(scope)));
    }
}
