package com.henrymorgandibie.switchyard.network.management;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.messaging.kafka.NetworkEventPublisher;
import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;
import com.henrymorgandibie.switchyard.network.tcp.IsoTcpServer;
import com.henrymorgandibie.switchyard.network.tcp.IsoTcpServerConfig;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipantStatusRegistry;
import com.henrymorgandibie.switchyard.routing.domain.ParticipantStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves {@link DispatchingMessageHandler} and {@link NetworkManagementHandler} together over a
 * real TCP socket and the real codec - not just unit-tested in isolation. Uses a trivial stub in
 * place of the real transaction pipeline for 0200 traffic: this test's job is proving the
 * dispatcher's routing and the network management handler's wire-level behavior, not
 * re-proving the golden path (already covered, with the real pipeline and real Postgres, by
 * {@code GoldenPathIntegrationTest}) or reversals (covered by
 * {@code ReversalPipelineIntegrationTest}).
 */
class DispatchingMessageHandlerTcpIntegrationTest {

    private IsoTcpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Timeout(30)
    void signOnOverRealTcpMarksTheInstitutionUpAndReturnsA00() throws Exception {
        NetworkParticipantStatusRegistry statusRegistry = new NetworkParticipantStatusRegistry();
        server = startServer(statusRegistry, stubTransactionHandler());

        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), networkManagementRequest("000001", "001", "12345"));
            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));

            assertThat(response.mti()).isEqualTo(Mti.NETWORK_MANAGEMENT_RESPONSE);
            assertThat(response.stringField(39)).isEqualTo("00");
            assertThat(statusRegistry.statusOf("12345")).isEqualTo(ParticipantStatus.UP);
        }
    }

    @Test
    @Timeout(30)
    void echoTestOverRealTcpReturnsA00WithoutTouchingAnyStatus() throws Exception {
        NetworkParticipantStatusRegistry statusRegistry = new NetworkParticipantStatusRegistry();
        server = startServer(statusRegistry, stubTransactionHandler());

        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), networkManagementRequest("000002", "301", null));
            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));

            assertThat(response.stringField(39)).isEqualTo("00");
            assertThat(statusRegistry.statusOf("12345")).isEqualTo(ParticipantStatus.DOWN);
        }
    }

    @Test
    @Timeout(30)
    void aFinancialRequestOverTheSameGatewayStillReachesTheTransactionHandlerNotNetworkManagement()
            throws Exception {
        NetworkParticipantStatusRegistry statusRegistry = new NetworkParticipantStatusRegistry();
        server = startServer(statusRegistry, stubTransactionHandler());

        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), financialRequest("000003"));
            byte[] response = readFramed(socket.getInputStream());

            assertThat(new String(response, StandardCharsets.US_ASCII)).isEqualTo("stub-transaction-response");
        }
    }

    private static IsoMessageHandler stubTransactionHandler() {
        return (correlationId, requestBody) -> "stub-transaction-response".getBytes(StandardCharsets.US_ASCII);
    }

    private IsoTcpServer startServer(NetworkParticipantStatusRegistry statusRegistry, IsoMessageHandler transactionHandler) {
        // A mock is fine here - this test proves the dispatcher/handler wiring and status
        // registry, not Kafka publishing, which has its own dedicated real-broker test.
        NetworkManagementHandler networkManagementHandler =
                new NetworkManagementHandler(statusRegistry, mock(NetworkEventPublisher.class));
        DispatchingMessageHandler dispatcher = new DispatchingMessageHandler(transactionHandler, networkManagementHandler);
        IsoTcpServer newServer = new IsoTcpServer(IsoTcpServerConfig.defaults(0), dispatcher);
        newServer.start();
        return newServer;
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", server.boundPort());
        socket.setSoTimeout(10_000);
        return socket;
    }

    private static byte[] networkManagementRequest(String stan, String functionCode, String institutionId) {
        IsoMessage.Builder builder = IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910120700")
                .numeric(11, stan)
                .numeric(70, functionCode);
        if (institutionId != null) {
            builder.numeric(32, institutionId);
        }
        return IsoMessagePacker.pack(builder.build());
    }

    private static byte[] financialRequest(String stan) {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, stan)
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
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
            throw new EOFException("connection closed before a length prefix was received");
        }
        int length = (hi << 8) | lo;
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n < 0) {
                throw new EOFException("connection closed mid-frame");
            }
            read += n;
        }
        return body;
    }
}
