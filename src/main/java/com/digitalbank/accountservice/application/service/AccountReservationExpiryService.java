package com.digitalbank.accountservice.application.service;

import com.digitalbank.accountservice.application.port.in.ReservationView;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationEvent;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventFactory;
import com.digitalbank.accountservice.application.port.out.AccountReservationEventOutbox;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.model.Account;
import com.digitalbank.accountservice.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(
        prefix = "account.reservation.expiry",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AccountReservationExpiryService {

    private final AccountRepository accountRepository;
    private final AccountReservationRepository reservationRepository;
    private final Clock clock;
    private final int batchSize;
    private final AccountReservationEventOutbox eventOutbox;

    @Autowired
    public AccountReservationExpiryService(
            AccountRepository accountRepository,
            AccountReservationRepository reservationRepository,
            Clock clock,
            @Value("${account.reservation.expiry.batch-size:100}") int batchSize,
            AccountReservationEventOutbox eventOutbox) {
        this.accountRepository = accountRepository;
        this.reservationRepository = reservationRepository;
        this.clock = clock;
        this.batchSize = batchSize;
        this.eventOutbox = eventOutbox;
    }

    public AccountReservationExpiryService(
            AccountRepository accountRepository,
            AccountReservationRepository reservationRepository,
            Clock clock,
            int batchSize) {
        this(accountRepository, reservationRepository, clock, batchSize, (AccountReservationEventOutbox) null);
    }

    @Scheduled(
            fixedDelayString = "${account.reservation.expiry.sweep-delay-ms:30000}",
            initialDelayString = "${account.reservation.expiry.initial-delay-ms:30000}")
    @Transactional
    public void sweepExpiredReservations() {
        expireDueReservations();
    }

    @Transactional
    public int expireDueReservations() {
        var now = clock.instant();
        var expiredReservations = reservationRepository.findExpiredActiveForUpdate(now, batchSize);
        for (var reservation : expiredReservations) {
            var account = accountRepository
                    .findById(reservation.accountId())
                    .orElseThrow(() -> new IllegalStateException("Account for reservation is missing"));
            accountRepository.save(withRestoredAvailability(account, reservation.amount(), now));
            var expiredReservation = reservationRepository.save(asExpired(reservation, now));
            if (eventOutbox != null) {
                var causationId = reservation.acceptedEventId() == null
                        ? AccountReservationEventFactory.eventId(
                                        "AccountReservationAccepted.v1", reservation.reservationRequestId())
                                .toString()
                        : reservation.acceptedEventId().toString();
                eventOutbox.recordIfAbsent(new AccountReservationEvent(
                        AccountReservationEventFactory.eventId(
                                "AccountReservationExpired.v1", reservation.reservationRequestId()),
                        "AccountReservationExpired.v1",
                        AccountReservationEvent.SCHEMA_VERSION,
                        AccountReservationEvent.PRODUCER,
                        now,
                        reservation.reservationRequestId(),
                        reservation.correlationId(),
                        causationId,
                        reservation.transactionId(),
                        reservation.reservationRequestId(),
                        expiredReservation.reservationId(),
                        reservation.accountId().value(),
                        reservation.destinationAccountId() == null
                                ? null
                                : reservation.destinationAccountId().value(),
                        reservation.amount(),
                        reservation.currency(),
                        reservation.expiresAt(),
                        "EXPIRED",
                        null,
                        null,
                        null,
                        null));
            }
        }
        return expiredReservations.size();
    }

    private static Account withRestoredAvailability(Account account, BigDecimal amount, java.time.Instant now) {
        return new Account(
                account.id(),
                account.customerId(),
                account.accountNumber(),
                account.iban(),
                account.type(),
                account.currency(),
                account.status(),
                account.currentBalance(),
                account.availableBalance().add(amount),
                account.openingRequestId(),
                account.version(),
                account.createdAt(),
                now,
                account.closedAt());
    }

    private static ReservationView asExpired(ReservationView reservation, java.time.Instant now) {
        return new ReservationView(
                reservation.reservationId(),
                reservation.accountId(),
                reservation.reservationRequestId(),
                reservation.currency(),
                reservation.amount(),
                reservation.correlationId(),
                reservation.causationId(),
                ReservationStatus.EXPIRED,
                reservation.expiresAt(),
                reservation.version(),
                reservation.createdAt(),
                now,
                null,
                null);
    }
}
