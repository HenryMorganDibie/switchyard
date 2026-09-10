package com.henrymorgandibie.switchyard.routing.application;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerResponse;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import com.henrymorgandibie.switchyard.routing.domain.NoRouteException;
import com.henrymorgandibie.switchyard.routing.domain.RoutingRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses simple inline test doubles for {@link IssuerConnector}/{@link com.henrymorgandibie.switchyard.participant.acquirer.AcquirerConnector}
 * rather than the real Demo simulators - the router's own routing-decision logic is what this
 * class tests, independent of what a real simulator happens to do.
 */
class DefaultTransactionRouterTest {

    private static final NetworkParticipant ACQUIRER_A =
            new NetworkParticipant("ACQ-A", "Demo Acquirer A", NetworkParticipant.Role.ACQUIRER);
    private static final NetworkParticipant ISSUER_A =
            new NetworkParticipant("ISS-A", "Demo Issuer A", NetworkParticipant.Role.ISSUER);
    private static final NetworkParticipant ISSUER_B =
            new NetworkParticipant("ISS-B", "Demo Issuer B", NetworkParticipant.Role.ISSUER);

    @Test
    void resolvesTheIssuerNamedByTheActiveRoutingRule() {
        DefaultTransactionRouter router = new DefaultTransactionRouter(
                id -> id.equals("12345") ? Optional.of(ACQUIRER_A) : Optional.empty(),
                Map.of("ISS-A", stubIssuer(ISSUER_A)),
                List.of(new RoutingRule("ACQ-A", "ISS-A", 1, true)));

        IssuerConnector resolved = router.resolveIssuer("12345");

        assertThat(resolved.participant()).isEqualTo(ISSUER_A);
    }

    @Test
    void unrecognizedAcquirerIsARoutingFailure() {
        DefaultTransactionRouter router = new DefaultTransactionRouter(
                id -> Optional.empty(),
                Map.of("ISS-A", stubIssuer(ISSUER_A)),
                List.of(new RoutingRule("ACQ-A", "ISS-A", 1, true)));

        assertThatThrownBy(() -> router.resolveIssuer("99999"))
                .isInstanceOf(NoRouteException.class)
                .hasMessageContaining("unrecognized acquiring institution");
    }

    @Test
    void inactiveRoutingRuleIsIgnored() {
        DefaultTransactionRouter router = new DefaultTransactionRouter(
                id -> Optional.of(ACQUIRER_A),
                Map.of("ISS-A", stubIssuer(ISSUER_A)),
                List.of(new RoutingRule("ACQ-A", "ISS-A", 1, false)));

        assertThatThrownBy(() -> router.resolveIssuer("12345"))
                .isInstanceOf(NoRouteException.class)
                .hasMessageContaining("no active routing rule");
    }

    @Test
    void routingRuleReferencingAnUnregisteredIssuerIsARoutingFailure() {
        DefaultTransactionRouter router = new DefaultTransactionRouter(
                id -> Optional.of(ACQUIRER_A),
                Map.of(), // no issuers registered
                List.of(new RoutingRule("ACQ-A", "ISS-A", 1, true)));

        assertThatThrownBy(() -> router.resolveIssuer("12345"))
                .isInstanceOf(NoRouteException.class)
                .hasMessageContaining("unknown issuer");
    }

    @Test
    void whenMultipleActiveRulesExistTheLowestPriorityNumberWins() {
        DefaultTransactionRouter router = new DefaultTransactionRouter(
                id -> Optional.of(ACQUIRER_A),
                Map.of("ISS-A", stubIssuer(ISSUER_A), "ISS-B", stubIssuer(ISSUER_B)),
                List.of(
                        new RoutingRule("ACQ-A", "ISS-B", 5, true),
                        new RoutingRule("ACQ-A", "ISS-A", 1, true)));

        IssuerConnector resolved = router.resolveIssuer("12345");

        assertThat(resolved.participant()).isEqualTo(ISSUER_A);
    }

    private static IssuerConnector stubIssuer(NetworkParticipant participant) {
        return new IssuerConnector() {
            @Override
            public NetworkParticipant participant() {
                return participant;
            }

            @Override
            public IssuerResponse authorize(IsoMessage request) {
                return new IssuerResponse("00", "AUTH01");
            }
        };
    }
}
