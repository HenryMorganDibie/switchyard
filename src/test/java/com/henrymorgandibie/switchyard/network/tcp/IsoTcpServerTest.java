package com.henrymorgandibie.switchyard.network.tcp;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.network.tcp.support.EchoResponseHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Real-socket integration tests: a plain {@link Socket} client (not Netty) talks to a real
 * {@link IsoTcpServer} over localhost. This deliberately does not use Netty on the client side,
 * so these tests prove interop at the raw TCP level rather than Netty talking to itself.
 *
 * <p>The gateway is wired to {@link EchoResponseHandler}, which uses the real ISO 8583 codec to
 * unpack requests and build genuine approval responses - these tests exercise real message
 * parsing over real sockets, without pulling in the transaction pipeline (no persistence,
 * routing, or state machine - that wiring belongs to a later milestone).
 */
class IsoTcpServerTest {

    private IsoTcpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @Timeout(30)
    void singleRequestGetsTheCorrectResponseOverARealSocket() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());

        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), pack0200("000001"));
            byte[] responseBytes = readFramed(socket.getInputStream());

            IsoMessage response = IsoMessageUnpacker.unpack(responseBytes);
            assertThat(response.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
            assertThat(response.stringField(39)).isEqualTo("00");
            assertThat(response.stringField(11)).isEqualTo("000001");
        }
    }

    @Test
    @Timeout(30)
    void sequentialRequestsOnTheSameConnectionAreCorrectlyCorrelated() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());

        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), pack0200("000001"));
            IsoMessage firstResponse = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));

            writeFramed(socket.getOutputStream(), pack0400("000002"));
            IsoMessage secondResponse = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));

            assertThat(firstResponse.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
            assertThat(firstResponse.stringField(11)).isEqualTo("000001");
            assertThat(secondResponse.mti()).isEqualTo(Mti.REVERSAL_RESPONSE);
            assertThat(secondResponse.stringField(11)).isEqualTo("000002");
        }
    }

    @Test
    @Timeout(30)
    void fragmentedRequestWrittenAsSeparateChunksIsReassembledCorrectly() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());

        try (Socket socket = connect()) {
            byte[] body = pack0200("000003");
            OutputStream out = socket.getOutputStream();
            out.write((body.length >>> 8) & 0xFF);
            out.flush();
            Thread.sleep(50);
            out.write(body.length & 0xFF);
            out.flush();
            Thread.sleep(50);
            // write the body itself split into three chunks
            int third = body.length / 3;
            out.write(body, 0, third);
            out.flush();
            Thread.sleep(50);
            out.write(body, third, third);
            out.flush();
            Thread.sleep(50);
            out.write(body, 2 * third, body.length - 2 * third);
            out.flush();

            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));
            assertThat(response.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
            assertThat(response.stringField(11)).isEqualTo("000003");
        }
    }

    @Test
    @Timeout(30)
    void twoMessagesCoalescedIntoOneWriteAreBothHandledInOrder() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());

        try (Socket socket = connect()) {
            byte[] first = pack0200("000004");
            byte[] second = pack0200("000005");
            java.io.ByteArrayOutputStream combined = new java.io.ByteArrayOutputStream();
            appendFramed(combined, first);
            appendFramed(combined, second);

            socket.getOutputStream().write(combined.toByteArray());
            socket.getOutputStream().flush();

            IsoMessage firstResponse = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));
            IsoMessage secondResponse = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));

            assertThat(firstResponse.stringField(11)).isEqualTo("000004");
            assertThat(secondResponse.stringField(11)).isEqualTo("000005");
        }
    }

    @Test
    @Timeout(30)
    void concurrentConnectionsAreIsolatedFromEachOther() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0).withMaxConnections(50), new EchoResponseHandler());
        int clients = 20;
        ExecutorService pool = Executors.newFixedThreadPool(clients);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, clients)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        String stan = String.format("%06d", i);
                        try (Socket socket = connect()) {
                            writeFramed(socket.getOutputStream(), pack0200(stan));
                            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));
                            return response.stringField(11).equals(stan) && response.stringField(39).equals("00");
                        }
                    })
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertThat(result.get()).isTrue();
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Timeout(30)
    void oversizedFrameClosesThatConnectionButServerKeepsRunning() throws Exception {
        // default maxFrameLength (4096) - deliberately NOT shrunk, since a legitimate pack0200()
        // message is ~65 bytes and the second half of this test needs it to fit comfortably so
        // that only the first (genuinely oversized) connection gets rejected.
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());

        try (Socket socket = connect()) {
            // declare the largest possible 2-byte-prefix length (65535), far beyond the 4096 max
            socket.getOutputStream().write(new byte[]{(byte) 0xFF, (byte) 0xFF});
            socket.getOutputStream().flush();

            int result = socket.getInputStream().read();
            assertThat(result).as("connection should be closed by the server").isEqualTo(-1);
        }

        // server must still be accepting new, well-formed connections after the bad one
        try (Socket socket = connect()) {
            writeFramed(socket.getOutputStream(), pack0200("000006"));
            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(socket.getInputStream()));
            assertThat(response.stringField(39)).isEqualTo("00");
        }
    }

    @Test
    @Timeout(30)
    void idleConnectionIsClosedAfterTheConfiguredTimeout() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0).withIdleTimeoutSeconds(1), new EchoResponseHandler());

        try (Socket socket = connect()) {
            socket.setSoTimeout(5000);
            int result = socket.getInputStream().read(); // sends nothing, waits for the server to close it
            assertThat(result).isEqualTo(-1);
        }
    }

    @Test
    @Timeout(30)
    void connectionsBeyondTheLimitAreRejectedWhileExistingOnesKeepWorking() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0).withMaxConnections(2), new EchoResponseHandler());

        try (Socket first = connect(); Socket second = connect()) {
            waitUntilActiveConnectionCountAtLeast(2);
            try (Socket third = connect()) {
                third.setSoTimeout(3000);
                assertThat(third.getInputStream().read()).as("third connection should be rejected").isEqualTo(-1);
            }

            writeFramed(first.getOutputStream(), pack0200("000007"));
            IsoMessage response = IsoMessageUnpacker.unpack(readFramed(first.getInputStream()));
            assertThat(response.stringField(39)).isEqualTo("00");
        }
    }

    @Test
    @Timeout(30)
    void stopReleasesThePortAndClosesOpenConnections() throws Exception {
        server = startServer(IsoTcpServerConfig.defaults(0), new EchoResponseHandler());
        int port = server.boundPort();
        Socket openConnection = connect();
        // Connection registration (channelGroup.add(channel) inside the channel initializer)
        // happens asynchronously on Netty's own threads, after the client's connect() has
        // already returned - without this wait, stop() can race ahead of registration and the
        // channel is never tracked, so it's never closed (found via a real failure: the client
        // read below hit its SocketTimeoutException instead of seeing the connection close).
        waitUntilActiveConnectionCountAtLeast(1);

        server.stop();

        assertThatCode(() -> {
            try (ServerSocket rebindCheck = new ServerSocket()) {
                rebindCheck.setReuseAddress(true);
                rebindCheck.bind(new InetSocketAddress("127.0.0.1", port));
                // if this doesn't throw, the OS-level socket really was released
            }
        }).doesNotThrowAnyException();

        int result = openConnection.getInputStream().read();
        assertThat(result).as("previously open connection should be closed").isEqualTo(-1);
        openConnection.close();
        server = null; // already stopped; avoid double-stop in tearDown
    }

    private IsoTcpServer startServer(IsoTcpServerConfig config, IsoMessageHandler handler) {
        IsoTcpServer newServer = new IsoTcpServer(config, handler);
        newServer.start();
        return newServer;
    }

    /**
     * Every test socket gets a default read timeout so a test fails fast with
     * SocketTimeoutException instead of hanging forever if the server doesn't respond as
     * expected - a blocked plain {@link Socket} read does not reliably respond to JUnit's
     * {@code @Timeout} (thread interruption does not unblock classic blocking socket I/O the
     * way it does NIO), so relying on {@code @Timeout} alone here is not sufficient.
     */
    /**
     * Polls {@link IsoTcpServer#activeConnectionCount()} instead of a blind sleep - deterministic
     * and as fast as the server actually is, rather than guessing a fixed delay that could be
     * too short (flaky) or needlessly long (slow) depending on machine load.
     */
    private void waitUntilActiveConnectionCountAtLeast(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (server.activeConnectionCount() < expected) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("timed out waiting for activeConnectionCount() >= " + expected
                        + ", was " + server.activeConnectionCount());
            }
            Thread.sleep(10);
        }
    }

    private Socket connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", server.boundPort());
        socket.setSoTimeout(10_000);
        return socket;
    }

    private static byte[] pack0200(String stan) {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, stan)
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }

    private static byte[] pack0400(String stan) {
        IsoMessage message = IsoMessage.builder(Mti.REVERSAL_REQUEST)
                .numeric(3, "020000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120600")
                .numeric(11, stan)
                .ans(37, "RRN000000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
        return IsoMessagePacker.pack(message);
    }

    private static void writeFramed(OutputStream out, byte[] body) throws IOException {
        appendFramed(out, body);
        out.flush();
    }

    private static void appendFramed(OutputStream out, byte[] body) throws IOException {
        out.write((body.length >>> 8) & 0xFF);
        out.write(body.length & 0xFF);
        out.write(body);
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
