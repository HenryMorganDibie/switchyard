package com.henrymorgandibie.switchyard.network.management;

import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchingMessageHandlerTest {

    @Test
    void a0800MessageIsRoutedToTheNetworkManagementHandler() {
        AtomicInteger networkManagementCalls = new AtomicInteger();
        AtomicInteger transactionCalls = new AtomicInteger();
        IsoMessageHandler networkManagementHandler = countingHandler(networkManagementCalls, "network-management-response".getBytes(StandardCharsets.US_ASCII));
        IsoMessageHandler transactionHandler = countingHandler(transactionCalls, "transaction-response".getBytes(StandardCharsets.US_ASCII));
        DispatchingMessageHandler dispatcher = new DispatchingMessageHandler(transactionHandler, networkManagementHandler);

        byte[] response = dispatcher.handle("corr-1", "0800".getBytes(StandardCharsets.US_ASCII));

        assertThat(networkManagementCalls.get()).isEqualTo(1);
        assertThat(transactionCalls.get()).isZero();
        assertThat(new String(response, StandardCharsets.US_ASCII)).isEqualTo("network-management-response");
    }

    @Test
    void a0200MessageIsRoutedToTheTransactionHandler() {
        AtomicInteger networkManagementCalls = new AtomicInteger();
        AtomicInteger transactionCalls = new AtomicInteger();
        IsoMessageHandler networkManagementHandler = countingHandler(networkManagementCalls, "network-management-response".getBytes(StandardCharsets.US_ASCII));
        IsoMessageHandler transactionHandler = countingHandler(transactionCalls, "transaction-response".getBytes(StandardCharsets.US_ASCII));
        DispatchingMessageHandler dispatcher = new DispatchingMessageHandler(transactionHandler, networkManagementHandler);

        byte[] response = dispatcher.handle("corr-2", "0200".getBytes(StandardCharsets.US_ASCII));

        assertThat(transactionCalls.get()).isEqualTo(1);
        assertThat(networkManagementCalls.get()).isZero();
        assertThat(new String(response, StandardCharsets.US_ASCII)).isEqualTo("transaction-response");
    }

    @Test
    void a0400MessageIsRoutedToTheTransactionHandler() {
        AtomicInteger transactionCalls = new AtomicInteger();
        IsoMessageHandler networkManagementHandler = countingHandler(new AtomicInteger(), new byte[0]);
        IsoMessageHandler transactionHandler = countingHandler(transactionCalls, new byte[0]);
        DispatchingMessageHandler dispatcher = new DispatchingMessageHandler(transactionHandler, networkManagementHandler);

        dispatcher.handle("corr-3", "0400".getBytes(StandardCharsets.US_ASCII));

        assertThat(transactionCalls.get()).isEqualTo(1);
    }

    @Test
    void aBodyShorterThanAnMtiIsRoutedToTheTransactionHandlerRatherThanCrashingTheDispatcher() {
        AtomicInteger transactionCalls = new AtomicInteger();
        IsoMessageHandler networkManagementHandler = countingHandler(new AtomicInteger(), new byte[0]);
        IsoMessageHandler transactionHandler = countingHandler(transactionCalls, new byte[0]);
        DispatchingMessageHandler dispatcher = new DispatchingMessageHandler(transactionHandler, networkManagementHandler);

        dispatcher.handle("corr-4", "08".getBytes(StandardCharsets.US_ASCII));

        assertThat(transactionCalls.get())
                .as("a too-short body should reach the transaction handler's own unpack, which produces the "
                        + "real malformed-message error - not be silently misrouted or crash the dispatcher itself")
                .isEqualTo(1);
    }

    private static IsoMessageHandler countingHandler(AtomicInteger callCount, byte[] response) {
        return (correlationId, requestBody) -> {
            callCount.incrementAndGet();
            return response;
        };
    }
}
