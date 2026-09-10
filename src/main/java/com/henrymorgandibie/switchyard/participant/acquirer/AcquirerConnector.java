package com.henrymorgandibie.switchyard.participant.acquirer;

import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;

import java.util.Optional;

/**
 * Resolves an acquiring institution ID (ISO 8583 DE32) to the {@link NetworkParticipant} that
 * submitted a transaction, or empty if it is not a recognized acquirer. The router uses this to
 * validate the source side of a transaction before routing it to an issuer - an unrecognized
 * acquirer is a routing failure just as much as an unrecognized issuer is.
 */
public interface AcquirerConnector {

    Optional<NetworkParticipant> resolve(String acquiringInstitutionId);
}
