package com.henrymorgandibie.switchyard.transaction.application;

import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.exception.RequiredFieldMissingException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerResponse;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import com.henrymorgandibie.switchyard.routing.domain.NoRouteException;
import com.henrymorgandibie.switchyard.routing.domain.TransactionRouter;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionEventRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests the pipeline's orchestration logic (which state transitions happen, what response
 * comes back) against mocked repositories and a stub router/issuer - persistence correctness
 * itself is already covered by the transaction-domain milestone's repository tests. The real,
 * fully-integrated proof (actual Postgres, actual TCP socket) is the golden-path test.
 */
@ExtendWith(MockitoExtension.class)
class TransactionProcessingPipelineTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private TransactionEventRepository eventRepository;

    @Test
    void approvedFlowTransitionsThroughToApprovedAndReturnsA00Response() {
        NetworkParticipant issuerParticipant =
                new NetworkParticipant("ISS-A", "Demo Issuer A", NetworkParticipant.Role.ISSUER);
        IssuerConnector issuer = stubIssuer(issuerParticipant, new IssuerResponse("00", "AUTH01"));
        TransactionRouter router = acquiringInstitutionId -> issuer;

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionProcessingPipeline pipeline =
                new TransactionProcessingPipeline(transactionRepository, eventRepository, router);

        byte[] responseBytes = pipeline.handle("corr-1", pack0200("000001"));
        IsoMessage response = IsoMessageUnpacker.unpack(responseBytes);

        assertThat(response.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
        assertThat(response.stringField(39)).isEqualTo("00");
        assertThat(response.stringField(38)).isEqualTo("AUTH01");

        ArgumentCaptor<Transaction> savedCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).saveAndFlush(savedCaptor.capture());
        Transaction finalState = savedCaptor.getAllValues().get(savedCaptor.getAllValues().size() - 1);
        assertThat(finalState.state()).isEqualTo(TransactionState.APPROVED);
        assertThat(finalState.responseCode()).isEqualTo("00");
    }

    @Test
    void declinedIssuerResponseTransitionsToDeclined() {
        NetworkParticipant issuerParticipant =
                new NetworkParticipant("ISS-A", "Demo Issuer A", NetworkParticipant.Role.ISSUER);
        IssuerConnector issuer = stubIssuer(issuerParticipant, new IssuerResponse("51", null));
        TransactionRouter router = acquiringInstitutionId -> issuer;

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionProcessingPipeline pipeline =
                new TransactionProcessingPipeline(transactionRepository, eventRepository, router);

        byte[] responseBytes = pipeline.handle("corr-2", pack0200("000002"));
        IsoMessage response = IsoMessageUnpacker.unpack(responseBytes);

        assertThat(response.stringField(39)).isEqualTo("51");
        assertThat(response.hasField(38)).isFalse();

        ArgumentCaptor<Transaction> savedCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).saveAndFlush(savedCaptor.capture());
        Transaction finalState = savedCaptor.getAllValues().get(savedCaptor.getAllValues().size() - 1);
        assertThat(finalState.state()).isEqualTo(TransactionState.DECLINED);
    }

    @Test
    void noRouteTransitionsToFailedAndReturnsResponseCode96() {
        TransactionRouter router = acquiringInstitutionId -> {
            throw new NoRouteException("unrecognized acquiring institution: " + acquiringInstitutionId);
        };

        when(transactionRepository.saveAndFlush(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionProcessingPipeline pipeline =
                new TransactionProcessingPipeline(transactionRepository, eventRepository, router);

        byte[] responseBytes = pipeline.handle("corr-3", pack0200("000003"));
        IsoMessage response = IsoMessageUnpacker.unpack(responseBytes);

        assertThat(response.stringField(39)).isEqualTo("96");

        ArgumentCaptor<Transaction> savedCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository, atLeastOnce()).saveAndFlush(savedCaptor.capture());
        Transaction finalState = savedCaptor.getAllValues().get(savedCaptor.getAllValues().size() - 1);
        assertThat(finalState.state()).isEqualTo(TransactionState.FAILED);
        assertThat(finalState.responseCode()).isEqualTo("96");
    }

    @Test
    void unsupportedMtiIsRejectedBeforeAnyPersistence() {
        TransactionRouter router = acquiringInstitutionId -> {
            throw new AssertionError("router should not be called for an unsupported MTI");
        };
        TransactionProcessingPipeline pipeline =
                new TransactionProcessingPipeline(transactionRepository, eventRepository, router);

        byte[] networkManagementRequest = IsoMessagePacker.pack(IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910120700")
                .numeric(11, "000004")
                .build());

        assertThatThrownBy(() -> pipeline.handle("corr-4", networkManagementRequest))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void missingRequiredFieldIsRejectedBeforeAnyPersistence() {
        TransactionRouter router = acquiringInstitutionId -> {
            throw new AssertionError("router should not be called when required-field validation fails");
        };
        TransactionProcessingPipeline pipeline =
                new TransactionProcessingPipeline(transactionRepository, eventRepository, router);

        // Missing DE32 (acquiring institution) - required as of this milestone.
        byte[] incomplete = IsoMessagePacker.pack(IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000005")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build());

        assertThatThrownBy(() -> pipeline.handle("corr-5", incomplete))
                .isInstanceOf(RequiredFieldMissingException.class);
    }

    private static IssuerConnector stubIssuer(NetworkParticipant participant, IssuerResponse response) {
        return new IssuerConnector() {
            @Override
            public NetworkParticipant participant() {
                return participant;
            }

            @Override
            public IssuerResponse authorize(IsoMessage request) {
                return response;
            }
        };
    }

    private static byte[] pack0200(String stan) {
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
}
