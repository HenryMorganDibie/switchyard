package com.henrymorgandibie.switchyard.transaction.application;

import com.henrymorgandibie.switchyard.idempotency.IdempotencyKeys;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessagePacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoMessageUnpacker;
import com.henrymorgandibie.switchyard.iso8583.codec.IsoResponseBuilder;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.iso8583.validation.MtiValidator;
import com.henrymorgandibie.switchyard.iso8583.validation.RequiredFieldsValidator;
import com.henrymorgandibie.switchyard.network.tcp.IsoMessageHandler;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerResponse;
import com.henrymorgandibie.switchyard.routing.domain.NoRouteException;
import com.henrymorgandibie.switchyard.routing.domain.TransactionRouter;
import com.henrymorgandibie.switchyard.transaction.domain.Transaction;
import com.henrymorgandibie.switchyard.transaction.domain.TransactionEvent;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionEventRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import com.henrymorgandibie.switchyard.transaction.state.TransactionState;
import com.henrymorgandibie.switchyard.transaction.state.TransactionStateMachine;

import java.util.EnumSet;
import java.util.Set;

/**
 * Wires the codec, validation, transaction domain, state machine, and routing built across this
 * milestone into one real pipeline: unpack -&gt; validate -&gt; persist -&gt; route -&gt; authorize -&gt;
 * respond. Implements {@link IsoMessageHandler} directly, so it plugs straight into the TCP
 * gateway built in an earlier milestone step.
 *
 * <p>Deliberately out of scope here: idempotency <em>checking</em> (only key computation),
 * Kafka event publishing, and metrics/observability - each is a later milestone. Network
 * management (0800/0810) is also out of scope: it doesn't route to an issuer at all, and gets
 * its own handling when that milestone builds it.
 *
 * <p>No {@code @Transactional} wraps the whole method deliberately: each persistence step
 * commits independently, so a transaction's audit trail (its {@code TransactionEvent} rows)
 * survives even if a later step in the same request fails - exactly the reasoning
 * {@link TransactionState}'s Javadoc gives for tracking explicit state instead of relying on
 * overall request success/failure.
 */
public final class TransactionProcessingPipeline implements IsoMessageHandler {

    private static final Set<Mti> SUPPORTED_REQUEST_MTIS =
            EnumSet.of(Mti.AUTHORIZATION_REQUEST, Mti.FINANCIAL_REQUEST, Mti.REVERSAL_REQUEST);
    private static final String RESPONSE_CODE_NO_ROUTE = "96"; // system malfunction

    private final TransactionRepository transactionRepository;
    private final TransactionEventRepository eventRepository;
    private final TransactionRouter router;

    public TransactionProcessingPipeline(TransactionRepository transactionRepository,
                                          TransactionEventRepository eventRepository,
                                          TransactionRouter router) {
        this.transactionRepository = transactionRepository;
        this.eventRepository = eventRepository;
        this.router = router;
    }

    @Override
    public byte[] handle(String correlationId, byte[] requestBody) {
        IsoMessage request = IsoMessageUnpacker.unpack(requestBody);
        MtiValidator.validate(request.mti().code());
        RequiredFieldsValidator.validate(request);

        if (!SUPPORTED_REQUEST_MTIS.contains(request.mti())) {
            throw new UnsupportedOperationException(
                    "TransactionProcessingPipeline does not yet handle MTI " + request.mti().code());
        }

        String acquiringInstitutionId = request.stringField(32);
        String terminalId = request.stringField(41);
        String stan = request.stringField(11);
        String processingCode = request.stringField(3);
        long amount = Long.parseLong(request.stringField(4));
        String rrn = request.hasField(37) ? request.stringField(37) : null;

        String idempotencyKey = IdempotencyKeys.compute(
                acquiringInstitutionId, terminalId, stan, request.stringField(7), processingCode, amount);

        Transaction transaction = Transaction.received(correlationId, request.mti().code(), stan, rrn,
                processingCode, amount, request.stringField(49), terminalId, acquiringInstitutionId,
                idempotencyKey);
        transaction = transactionRepository.saveAndFlush(transaction);
        recordEvent(transaction, null, TransactionState.RECEIVED, "message unpacked and validated");

        transaction = transitionAndRecord(transaction, TransactionState.VALIDATING, null);
        transaction = transitionAndRecord(transaction, TransactionState.VALIDATED, null);
        transaction = transitionAndRecord(transaction, TransactionState.ROUTING, null);

        IssuerConnector issuer;
        try {
            issuer = router.resolveIssuer(acquiringInstitutionId);
        } catch (NoRouteException e) {
            transaction = transitionAndRecord(transaction, TransactionState.FAILED, e.getMessage());
            transaction.recordResponseCode(RESPONSE_CODE_NO_ROUTE);
            transactionRepository.saveAndFlush(transaction);
            IsoMessage response = IsoResponseBuilder.buildResponse(
                    request, request.mti().responseMti(), RESPONSE_CODE_NO_ROUTE);
            return IsoMessagePacker.pack(response);
        }

        transaction = transitionAndRecord(transaction, TransactionState.SENT_TO_ISSUER,
                "routed to " + issuer.participant().code());

        IssuerResponse issuerResponse = issuer.authorize(request);
        TransactionState outcome = issuerResponse.approved() ? TransactionState.APPROVED : TransactionState.DECLINED;
        transaction = transitionAndRecord(transaction, outcome, "issuer response code " + issuerResponse.responseCode());
        transaction.recordResponseCode(issuerResponse.responseCode());
        transactionRepository.saveAndFlush(transaction);

        IsoMessage response = IsoResponseBuilder.buildResponse(request, request.mti().responseMti(),
                issuerResponse.responseCode(), issuerResponse.authorizationId());
        return IsoMessagePacker.pack(response);
    }

    /**
     * Returns the freshly-managed {@link Transaction} from the save - callers must use this
     * return value for any further persistence, not the instance they passed in. Each call runs
     * in its own transaction (see the class Javadoc), so {@code saveAndFlush} performs a
     * {@code merge()} of what is, by the next call, a detached entity; JPA's merge() contract
     * returns a new managed instance rather than mutating the one passed in, so reusing the old
     * reference silently carries a stale {@code @Version} value into the next call and trips
     * optimistic-locking's "row was already updated by another transaction" check even though
     * nothing was actually concurrent - found via the golden-path test failing for exactly this
     * reason.
     */
    private Transaction transitionAndRecord(Transaction transaction, TransactionState to, String detail) {
        TransactionState from = transaction.state();
        TransactionStateMachine.transition(transaction, to);
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        recordEvent(saved, from, to, detail);
        return saved;
    }

    private void recordEvent(Transaction transaction, TransactionState from, TransactionState to, String detail) {
        eventRepository.saveAndFlush(TransactionEvent.of(transaction.id(), from, to, detail));
    }
}
