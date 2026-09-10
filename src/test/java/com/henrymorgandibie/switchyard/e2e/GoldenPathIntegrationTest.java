package com.henrymorgandibie.switchyard.e2e;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.network.tcp.IsoTcpServer;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Milestone A acceptance test: an unmocked, end-to-end proof that a real ISO 8583 0200
 * message sent over a real TCP socket is decoded, validated, routed, authorized, persisted, and
 * answered with a real 0210 - every step built across this milestone exercised together
 * (codec, TCP gateway, transaction domain, state machine, routing, DemoIssuer simulator), none
 * bypassed or mocked.
 *
 * <p>Uses a Testcontainers-provisioned Postgres, not the manually-started {@code docker compose}
 * stack the rest of the suite runs against, so this specific test is fully self-contained - per
 * the plan's explicit requirement for this one test. Redis/Kafka are not provisioned: nothing in
 * this milestone's pipeline uses them (idempotency checking and event publishing are later
 * milestones), and Spring's auto-configuration for both is lazy, so the context starts fine
 * without either running.
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "switchyard.tcp.enabled=true",
        "switchyard.tcp.port=0"
})
class GoldenPathIntegrationTest {

    @Container
    static PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private IsoTcpServer server;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @Timeout(30)
    void financialRequestOverRealTcpIsProcessedEndToEndAndPersistedApproved() throws IOException {
        String stan = "100001";
        byte[] requestBytes = IsoMessagePacker.pack(IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(2, "4111111111111111")
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, stan)
                .numeric(32, "12345") // matches DemoAcquirer's registered institution id
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build());

        byte[] responseBytes;
        try (Socket socket = new Socket("127.0.0.1", server.boundPort())) {
            socket.setSoTimeout(10_000);
            writeFramed(socket.getOutputStream(), requestBytes);
            responseBytes = readFramed(socket.getInputStream());
        }

        IsoMessage response = IsoMessageUnpacker.unpack(responseBytes);
        assertThat(response.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
        assertThat(response.stringField(39)).isEqualTo("00");
        assertThat(response.stringField(11)).isEqualTo(stan);
        assertThat(response.hasField(38)).isTrue();

        List<Transaction> all = transactionRepository.findAll();
        assertThat(all).hasSize(1);
        Transaction persisted = all.get(0);
        assertThat(persisted.stan()).isEqualTo(stan);
        assertThat(persisted.mti()).isEqualTo("0200");
        assertThat(persisted.state()).isEqualTo(TransactionState.APPROVED);
        assertThat(persisted.responseCode()).isEqualTo("00");
    }

    private static void writeFramed(OutputStream out, byte[] body) throws IOException {
        out.write((body.length >>> 8) & 0xFF);
        out.write(body.length & 0xFF);
        out.write(body);
        out.flush();
    }

    private static byte[] readFramed(InputStream in) throws IOException {
        int hi = in.read();
        int lo = in.read();
        if (hi < 0 || lo < 0) {
            throw new IOException("connection closed before a length prefix was received");
        }
        int length = (hi << 8) | lo;
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                throw new IOException("connection closed mid-frame");
            }
            read += n;
        }
        return body;
    }
}
