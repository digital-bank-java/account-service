package com.digitalbank.accountservice.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.in.ReserveFundsCommand;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.application.port.out.AccountSearchCriteria;
import com.digitalbank.accountservice.application.port.out.AccountSearchResult;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.AccountId;
import com.digitalbank.accountservice.domain.model.AccountStatus;
import com.digitalbank.accountservice.domain.model.AccountType;
import com.digitalbank.accountservice.domain.model.CustomerId;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccountReservationExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:15:30Z");

    private final InMemoryAccountRepository accountRepository = new InMemoryAccountRepository();
    private final InMemoryReservationRepository reservationRepository = new InMemoryReservationRepository();
    private final AccountReservationExpiryService service = new AccountReservationExpiryService(
            accountRepository, reservationRepository, Clock.fixed(NOW, ZoneOffset.UTC), 10);

    @Test
    void expiresDueActiveHoldsAndRestoresAvailabilityAcrossAccountStatuses() {
        var suspendedAccount = accountRepository.save(account(AccountStatus.SUSPENDED, "75.00"));
        var activeAccount = accountRepository.save(account(AccountStatus.ACTIVE, "75.00"));
        var expired = reservation(suspendedAccount, "expired-hold", NOW);
        var future = reservation(activeAccount, "future-hold", NOW.plusSeconds(1));
        reservationRepository.save(expired);
        reservationRepository.save(future);

        var expiredCount = service.expireDueReservations();

        assertThat(expiredCount).isOne();
        assertThat(reservationRepository
                        .findByReservationRequestId("expired-hold")
                        .orElseThrow()
                        .status())
                .isEqualTo(ReservationStatus.EXPIRED);
        assertThat(accountRepository
                        .findById(suspendedAccount.id())
                        .orElseThrow()
                        .availableBalance())
                .isEqualByComparingTo("100.00");
        assertThat(reservationRepository.findByReservationRequestId("future-hold"))
                .contains(future);
        assertThat(accountRepository.findById(activeAccount.id()).orElseThrow().availableBalance())
                .isEqualByComparingTo("75.00");
    }

    private static Account account(AccountStatus status, String availableBalance) {
        return new Account(
                AccountId.newId(),
                CustomerId.newId(),
                "1000000001" + status,
                null,
                AccountType.CURRENT,
                "AED",
                status,
                new BigDecimal("100.00"),
                new BigDecimal(availableBalance),
                "open-request-" + status,
                0L,
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600),
                null);
    }

    private static ReservationView reservation(Account account, String requestId, Instant expiresAt) {
        return new ReservationView(
                java.util.UUID.randomUUID(),
                account.id(),
                requestId,
                "AED",
                new BigDecimal("25.00"),
                "correlation-" + requestId,
                "causation-" + requestId,
                ReservationStatus.ACTIVE,
                expiresAt,
                0L,
                NOW.minusSeconds(900),
                NOW.minusSeconds(900));
    }

    private static final class InMemoryAccountRepository implements AccountRepository {

        private final List<Account> accounts = new ArrayList<>();

        @Override
        public Account save(Account account) {
            accounts.removeIf(candidate -> candidate.id().equals(account.id()));
            accounts.add(account);
            return account;
        }

        @Override
        public Optional<Account> findById(AccountId accountId) {
            return accounts.stream()
                    .filter(account -> account.id().equals(accountId))
                    .findFirst();
        }

        @Override
        public List<Account> findByCustomerId(CustomerId customerId) {
            return List.of();
        }

        @Override
        public AccountSearchResult search(AccountSearchCriteria criteria) {
            return new AccountSearchResult(List.of(), criteria.pageNumber(), criteria.pageSize(), 0, 0, true);
        }
    }

    private static final class InMemoryReservationRepository implements AccountReservationRepository {

        private final List<ReservationView> reservations = new ArrayList<>();

        @Override
        public Optional<ReservationView> findByReservationRequestId(String reservationRequestId) {
            return reservations.stream()
                    .filter(reservation -> reservation.reservationRequestId().equals(reservationRequestId))
                    .findFirst();
        }

        @Override
        public List<ReservationView> findExpiredActiveForUpdate(Instant expiresBy, int limit) {
            return reservations.stream()
                    .filter(reservation -> reservation.status() == ReservationStatus.ACTIVE)
                    .filter(reservation -> !reservation.expiresAt().isAfter(expiresBy))
                    .limit(limit)
                    .toList();
        }

        @Override
        public ReservationView save(ReserveFundsCommand command, Account account, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReservationView save(ReservationView reservation) {
            reservations.removeIf(existing -> existing.reservationId().equals(reservation.reservationId()));
            reservations.add(reservation);
            return reservation;
        }
    }
}
