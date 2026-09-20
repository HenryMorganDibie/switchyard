package com.henrymorgandibie.switchyard.transaction.application;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives a real financial approval through to a switch-assigned RRN, then a real reversal
 * referencing that RRN, through {@link TransactionProcessingPipeline} against the real,
 * fully-wired switch (docker-compose Postgres/Redis, same pattern as
 * {@link IdempotencyIntegrationTest}) - the end-to-end proof that reversals actually work, as
 * opposed to {@link com.henrymorgandibie.switchyard.reversal.ReversalServiceTest}'s mocked unit
 * coverage of the orchestration logic in isolation.
 */
@SpringBootTest
@TestPropertySource(properties = "switchyard.tcp.enabled=false")
class ReversalPipelineIntegrationTest {

    private static final String RUN_PREFIX = String.format("%03d", System.currentTimeMillis() % 1000);

    @Autowired
    private TransactionProcessingPipeline pipeline;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void approvalCarriesASwitchAssignedRrnAndAReversalReferencingItReversesTheOriginal() {
        String originalStan = RUN_PREFIX + "201";
        String reversalStan = RUN_PREFIX + "202";

        byte[] originalRequest = financialRequest(originalStan, "0910140001", "000000005000");
        IsoMessage originalResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-f1", originalRequest));

        assertThat(originalResponse.stringField(39)).isEqualTo("00");
        assertThat(originalResponse.hasField(37))
                .as("a switch-assigned RRN (DE37) must be present on an approval response")
                .isTrue();
        String assignedRrn = originalResponse.stringField(37);
        assertThat(assignedRrn).hasSize(12);

        Transaction persistedOriginal = findByStan(originalStan);
        assertThat(persistedOriginal.rrn()).isEqualTo(assignedRrn);
        assertThat(persistedOriginal.state()).isEqualTo(TransactionState.APPROVED);

        byte[] reversalRequest = reversalRequest(reversalStan, "0910140002", "000000005000", assignedRrn);
        IsoMessage reversalResponse = IsoMessageUnpacker.unpack(pipeline.handle("corr-f2", reversalRequest));

        assertThat(reversalResponse.mti()).isEqualTo(Mti.REVERSAL_RESPONSE);
        assertThat(reversalResponse.stringField(39)).isEqualTo("00");

        Transaction reloadedOriginal = transactionRepository.findById(persistedOriginal.id()).orElseThrow();
        assertThat(reloadedOriginal.state()).isEqualTo(TransactionState.REVERSED);
    }

    @Test
    void reversalReferencingAnUnknownRrnIsDeclinedWithoutCrashing() {
        String reversalStan = RUN_PREFIX + "203";
        byte[] reversalRequest = reversalRequest(reversalStan, "0910140003", "000000001000", "NOSUCHRRN001");

        IsoMessage response = IsoMessageUnpacker.unpack(pipeline.handle("corr-f3", reversalRequest));

        assertThat(response.mti()).isEqualTo(Mti.REVERSAL_RESPONSE);
        assertThat(response.stringField(39)).isEqualTo("25"); // unable to locate record

        Transaction reversalTransaction = findByStan(reversalStan);
        assertThat(reversalTransaction.state()).isEqualTo(TransactionState.DECLINED);
    }

    private Transaction findByStan(String stan) {
        List<Transaction> matches = transactionRepository.findAll().stream()
                .filter(t -> t.stan().equals(stan))
                .toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }

    private static byte[] financialRequest(String stan, String transmissionDateTime, String amount) {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, amount)
                .numeric(7, transmissionDateTime)
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }

    private static byte[] reversalRequest(String stan, String transmissionDateTime, String amount, String rrn) {
        IsoMessage message = IsoMessage.builder(Mti.REVERSAL_REQUEST)
                .numeric(3, "020000")
                .numeric(4, amount)
                .numeric(7, transmissionDateTime)
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(37, rrn)
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }
}
