package com.digitalbank.accountservice.adapter.in.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeInputPort;
import com.digitalbank.accountservice.application.service.LedgerPostingEventMapper;

class LedgerPostingKafkaListenerTest {

	@Test
	void delegatesACompletedKafkaRecordToTheExistingOutcomePort() {
		var event = completedEvent();
		var record = new ConsumerRecord<>("ledger.posting.completed.v1", 0, 0L, "aggregate-001", "{}");
		var command = new LedgerPostingOutcomeCommand(
				UUID.randomUUID().toString(), "posting-001", "reservation-001", LedgerPostingOutcome.COMPLETED, null);
		var parser = new CapturingParser(event);
		var mapper = new CapturingMapper(command);
		var outcomePort = new CapturingOutcomePort();
		var listener = new LedgerPostingKafkaListener(parser, mapper, outcomePort);

		listener.consumeCompleted(record);

		assertThat(parser.payload).isEqualTo("{}");
		assertThat(parser.expectedEventType).isEqualTo("LedgerPostingCompleted.v1");
		assertThat(mapper.event).isSameAs(event);
		assertThat(outcomePort.command).isEqualTo(command);
	}

	private static GovernedLedgerPostingEvent.Completed completedEvent() {
		var postingId = UUID.fromString("32ef46b9-480c-4fc3-b769-fcc5d1d6d262");
		return new GovernedLedgerPostingEvent.Completed(
				new GovernedLedgerPostingEvent.Metadata(
						UUID.fromString("5250517d-c99b-42a3-9388-b106a49a2d3b"),
						"transfer-001",
						"command-001",
						"ledger-service",
						"1.0.0",
						Instant.parse("2026-08-31T00:00:00Z")),
				postingId,
				"transfer-001",
				"reservation-001",
				postingId,
				"posting-request-001",
				null,
				"USD",
				List.of(
						new GovernedLedgerPostingEvent.Line(UUID.fromString("3286f9d5-3b44-4d2d-b9bb-0cfc15b395bb"), "DEBIT", "10.0000"),
						new GovernedLedgerPostingEvent.Line(UUID.fromString("09dcd396-e702-4d6a-836c-3869c808bf2c"), "CREDIT", "10.0000")));
	}

	private static final class CapturingParser extends LedgerPostingEventParser {

		private final GovernedLedgerPostingEvent event;
		private String payload;
		private String expectedEventType;

		private CapturingParser(GovernedLedgerPostingEvent event) {
			this.event = event;
		}

		@Override
		public GovernedLedgerPostingEvent parse(String payload, Map<String, String> headers, String expectedEventType) {
			this.payload = payload;
			this.expectedEventType = expectedEventType;
			return event;
		}
	}

	private static final class CapturingMapper extends LedgerPostingEventMapper {

		private final LedgerPostingOutcomeCommand command;
		private GovernedLedgerPostingEvent event;

		private CapturingMapper(LedgerPostingOutcomeCommand command) {
			super(null, null);
			this.command = command;
		}

		@Override
		public LedgerPostingOutcomeCommand toCommand(GovernedLedgerPostingEvent event) {
			this.event = event;
			return command;
		}
	}

	private static final class CapturingOutcomePort implements LedgerPostingOutcomeInputPort {

		private LedgerPostingOutcomeCommand command;

		@Override
		public com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeResult handle(
				LedgerPostingOutcomeCommand command) {
			this.command = command;
			return null;
		}
	}
}
