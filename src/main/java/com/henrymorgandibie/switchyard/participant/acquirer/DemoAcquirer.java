package com.henrymorgandibie.switchyard.participant.acquirer;

import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;

import java.util.Map;
import java.util.Optional;

/**
 * A simulated acquirer registry: recognizes a fixed set of acquiring institution IDs (ISO 8583
 * DE32 values), each mapped to a {@link NetworkParticipant}. The registry is supplied at
 * construction rather than persisted - see {@code RoutingRule}'s Javadoc for the same reasoning
 * (nothing mutates it at runtime yet).
 */
public final class DemoAcquirer implements AcquirerConnector {

    private final Map<String, NetworkParticipant> participantsByInstitutionId;

    public DemoAcquirer(Map<String, NetworkParticipant> participantsByInstitutionId) {
        for (NetworkParticipant participant : participantsByInstitutionId.values()) {
            if (participant.role() != NetworkParticipant.Role.ACQUIRER) {
                throw new IllegalArgumentException("DemoAcquirer requires ACQUIRER participants, got "
                        + participant.role() + " for " + participant.code());
            }
        }
        this.participantsByInstitutionId = participantsByInstitutionId;
    }

    @Override
    public Optional<NetworkParticipant> resolve(String acquiringInstitutionId) {
        return Optional.ofNullable(participantsByInstitutionId.get(acquiringInstitutionId));
    }
}
