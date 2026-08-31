package com.digitalbank.accountservice.application.service;

import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.out.AccountRepository;
import com.digitalbank.accountservice.application.port.out.AccountReservationRepository;
import com.digitalbank.accountservice.domain.exception.InvalidLedgerPostingEventException;
import com.digitalbank.accountservice.domain.exception.ReservationNotFoundException;

@Service
public class LedgerPostingEventMapper {

	private static final Pattern POSITIVE_DECIMAL_AMOUNT = Pattern.compile("^(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,4})?$");

	private final AccountReservationRepository reservationRepository;
	private final AccountRepository accountRepository;

	public LedgerPostingEventMapper(
			AccountReservationRepository reservationRepository,
			AccountRepository accountRepository) {
		this.reservationRepository = reservationRepository;
		this.accountRepository = accountRepository;
	}

	public LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event) {
		var reservation = reservationRepository.findByReservationRequestId(event.reservationRequestId())
				.orElseThrow(() -> new ReservationNotFoundException(event.reservationRequestId()));
		var account = accountRepository.findById(reservation.accountId())
				.orElseThrow(() -> new IllegalStateException("Account for reservation is missing"));

		return switch (event) {
			case GovernedLedgerPostingEvent.Completed completed -> completedCommand(completed, reservation, account);
			case GovernedLedgerPostingEvent.Failed failed -> new LedgerPostingOutcomeCommand(
					failed.metadata().eventId().toString(),
					failed.postingRequestId(),
					failed.reservationRequestId(),
					LedgerPostingOutcome.FAILED,
					null);
		};
	}

	private static LedgerPostingOutcomeCommand completedCommand(
			GovernedLedgerPostingEvent.Completed completed,
			com.digitalbank.accountservice.application.port.in.ReservationView reservation,
			com.digitalbank.accountservice.domain.model.Account account) {
		if (!completed.currency().equals(reservation.currency()) || !completed.currency().equals(account.currency())) {
			throw new InvalidLedgerPostingEventException("Completed posting currency must match the reservation account");
		}
		completed.lines().forEach(line -> {
			if (line.amount() == null || !POSITIVE_DECIMAL_AMOUNT.matcher(line.amount()).matches()
					|| new java.math.BigDecimal(line.amount()).signum() <= 0) {
				throw new InvalidLedgerPostingEventException("Ledger line amount must be a positive decimal string with at most four fractional digits");
			}
		});
		var matchingDebits = completed.lines().stream()
				.filter(line -> line.accountId().equals(reservation.accountId().value()))
				.filter(line -> "DEBIT".equals(line.lineType()))
				.filter(line -> new java.math.BigDecimal(line.amount()).compareTo(reservation.amount()) == 0)
				.count();
		if (matchingDebits != 1) {
			throw new InvalidLedgerPostingEventException("Completed posting must contain one debit line matching the reservation account and amount");
		}
		return new LedgerPostingOutcomeCommand(
				completed.metadata().eventId().toString(),
				completed.postingId().toString(),
				completed.reservationRequestId(),
				completed.reversalOfLedgerEntryId() == null ? LedgerPostingOutcome.COMPLETED : LedgerPostingOutcome.REVERSED,
				completed.reversalOfLedgerEntryId() == null ? null : completed.reversalOfLedgerEntryId().toString());
	}
}
