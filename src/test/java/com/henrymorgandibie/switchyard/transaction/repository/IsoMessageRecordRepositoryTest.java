package com.henrymorgandibie.switchyard.transaction.repository;

import com.henrymorgandibie.switchyard.transaction.domain.IsoMessageRecord;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class IsoMessageRecordRepositoryTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IsoMessageRecordRepository messageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void sanitizedPayloadJsonbRoundTripsAsAMap() {
        Transaction transaction = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000001", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        Map<String, String> payload = Map.of(
                "3", "000000",
                "4", "000000005000",
                "41", "TERM0001",
                "2", "411111******1111"); // masked PAN, as an application-layer masking step would produce

        IsoMessageRecord saved = messageRepository.saveAndFlush(
                IsoMessageRecord.of(transaction.id(), IsoMessageRecord.Direction.INBOUND, "0200", payload));
        entityManager.detach(saved);

        IsoMessageRecord reloaded = messageRepository.findById(saved.id()).orElseThrow();
        assertThat(reloaded.sanitizedPayload()).isEqualTo(payload);
        assertThat(reloaded.direction()).isEqualTo(IsoMessageRecord.Direction.INBOUND);
        assertThat(reloaded.mti()).isEqualTo("0200");
    }

    @Test
    void messagesForATransactionAreReturnedInCapturedAtOrder() {
        Transaction transaction = transactionRepository.saveAndFlush(Transaction.received(
                "corr-" + UUID.randomUUID(), "0200", "000002", null,
                "000000", 1000L, "566", "TERM0001", "12345", "idem-" + UUID.randomUUID()));

        messageRepository.saveAndFlush(IsoMessageRecord.of(
                transaction.id(), IsoMessageRecord.Direction.INBOUND, "0200", Map.of("11", "000002")));
        messageRepository.saveAndFlush(IsoMessageRecord.of(
                transaction.id(), IsoMessageRecord.Direction.OUTBOUND, "0210", Map.of("39", "00")));

        List<IsoMessageRecord> records = messageRepository.findByTransactionIdOrderByCapturedAtAsc(transaction.id());
        assertThat(records).hasSize(2);
        assertThat(records.get(0).direction()).isEqualTo(IsoMessageRecord.Direction.INBOUND);
        assertThat(records.get(1).direction()).isEqualTo(IsoMessageRecord.Direction.OUTBOUND);
    }
}
