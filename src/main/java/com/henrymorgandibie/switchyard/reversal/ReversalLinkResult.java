package com.henrymorgandibie.switchyard.reversal;

import java.util.UUID;

/** The outcome of {@link ReversalService#linkToOriginal}. */
public sealed interface ReversalLinkResult {

    record Linked(UUID originalTransactionId) implements ReversalLinkResult {
    }

    /** No RRN was referenced, or no transaction with that RRN exists - not an error. */
    record OriginalNotFound() implements ReversalLinkResult {
    }
}
