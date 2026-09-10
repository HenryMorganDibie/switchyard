package com.henrymorgandibie.switchyard.routing.domain;

import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;

/** Resolves which issuer a transaction from a given acquiring institution should be routed to. */
public interface TransactionRouter {

    /** @throws NoRouteException if no issuer can be resolved for this acquiring institution */
    IssuerConnector resolveIssuer(String acquiringInstitutionId);
}
