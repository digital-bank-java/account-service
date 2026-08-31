package com.digitalbank.accountservice.adapter.in.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;

import com.digitalbank.accountservice.application.port.in.GovernedLedgerPostingEvent;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcome;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeCommand;
import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeInputPort;
import com.digitalbank.accountservice.application.service.LedgerPostingEventMapper;

class LedgerPostingKafkaListenerTest {

	@Test
	void delegatesACompletedKafkaRecordToTheExistingOutcomePort() {
		var parser = mock(LedgerPostingEventParser.class);
		var mapper = mock(LedgerPostingEventMapper.class);
		var outcomePort = mock(LedgerPostingOutcomeInputPort.class);
		var listener = new LedgerPostingKafkaListener(parser, mapper, outcomePort);
		var record = new ConsumerRecord<>("ledger.posting.completed.v1", 0, 0L, "aggregate-001", "{}");
		var event = mock(GovernedLedgerPostingEvent.Completed.class);
		var command = new LedgerPostingOutcomeCommand(
				UUID.randomUUID().toString(), "posting-001", "reservation-001", LedgerPostingOutcome.COMPLETED, null);

		when(parser.parse(any(), any(), any())).thenReturn(event);
		when(mapper.toCommand(event)).thenReturn(command);

		listener.consumeCompleted(record);

		verify(mapper).toCommand(event);
		verify(outcomePort).handle(command);
	}
}
