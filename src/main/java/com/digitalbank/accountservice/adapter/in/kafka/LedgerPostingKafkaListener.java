package com.digitalbank.accountservice.adapter.in.kafka;

import com.digitalbank.accountservice.application.port.in.LedgerPostingOutcomeInputPort;
import com.digitalbank.accountservice.application.service.LedgerPostingEventMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "account.ledger.kafka", name = "enabled", havingValue = "true")
public class LedgerPostingKafkaListener {

    private static final String COMPLETED_EVENT_TYPE = "LedgerPostingCompleted.v1";
    private static final String FAILED_EVENT_TYPE = "LedgerPostingFailed.v1";

    private final LedgerPostingEventParser parser;
    private final LedgerPostingEventMapper mapper;
    private final LedgerPostingOutcomeInputPort outcomePort;

    public LedgerPostingKafkaListener(
            LedgerPostingEventParser parser,
            LedgerPostingEventMapper mapper,
            LedgerPostingOutcomeInputPort outcomePort) {
        this.parser = parser;
        this.mapper = mapper;
        this.outcomePort = outcomePort;
    }

    @KafkaListener(
            topics = "${account.ledger.kafka.completed-topic}",
            groupId = "${account.ledger.kafka.group-id}",
            containerFactory = "ledgerKafkaListenerContainerFactory")
    public void consumeCompleted(ConsumerRecord<String, String> record) {
        consume(record, COMPLETED_EVENT_TYPE);
    }

    @KafkaListener(
            topics = "${account.ledger.kafka.failed-topic}",
            groupId = "${account.ledger.kafka.group-id}",
            containerFactory = "ledgerKafkaListenerContainerFactory")
    public void consumeFailed(ConsumerRecord<String, String> record) {
        consume(record, FAILED_EVENT_TYPE);
    }

    private void consume(ConsumerRecord<String, String> record, String eventType) {
        var event = parser.parse(record.value(), headers(record), eventType);
        outcomePort.handle(mapper.toCommand(event));
    }

    private static java.util.Map<String, String> headers(ConsumerRecord<String, String> record) {
        var headers = new LinkedHashMap<String, String>();
        record.headers()
                .forEach(header -> headers.put(header.key(), new String(header.value(), StandardCharsets.UTF_8)));
        return java.util.Map.copyOf(headers);
    }
}
