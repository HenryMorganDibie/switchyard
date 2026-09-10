package com.henrymorgandibie.switchyard.routing.application;

import com.henrymorgandibie.switchyard.participant.acquirer.AcquirerConnector;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import com.henrymorgandibie.switchyard.routing.domain.NoRouteException;
import com.henrymorgandibie.switchyard.routing.domain.RoutingRule;
import com.henrymorgandibie.switchyard.routing.domain.TransactionRouter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Resolves an issuer in two steps: first confirm the acquiring institution is a recognized
 * participant (via {@link AcquirerConnector}), then find its highest-priority active
 * {@link RoutingRule} and look up the issuer it names. Either step failing is a routing failure
 * - an unrecognized acquirer is refused just as much as one with no configured destination.
 *
 * <p>Stateless given its constructor arguments (the rule list and connector maps are treated as
 * immutable inputs) - thread-safe to share a single instance.
 */
public final class DefaultTransactionRouter implements TransactionRouter {

    private final AcquirerConnector acquirerConnector;
    private final Map<String, IssuerConnector> issuersByCode;
    private final List<RoutingRule> rules;

    public DefaultTransactionRouter(AcquirerConnector acquirerConnector,
                                     Map<String, IssuerConnector> issuersByCode,
                                     List<RoutingRule> rules) {
        this.acquirerConnector = acquirerConnector;
        this.issuersByCode = issuersByCode;
        this.rules = rules;
    }

    @Override
    public IssuerConnector resolveIssuer(String acquiringInstitutionId) {
        NetworkParticipant acquirer = acquirerConnector.resolve(acquiringInstitutionId)
                .orElseThrow(() -> new NoRouteException(
                        "unrecognized acquiring institution: " + acquiringInstitutionId));

        RoutingRule rule = rules.stream()
                .filter(RoutingRule::active)
                .filter(r -> r.acquirerCode().equals(acquirer.code()))
                .min(Comparator.comparingInt(RoutingRule::priority))
                .orElseThrow(() -> new NoRouteException(
                        "no active routing rule for acquirer: " + acquirer.code()));

        IssuerConnector issuer = issuersByCode.get(rule.issuerCode());
        if (issuer == null) {
            throw new NoRouteException(
                    "routing rule for acquirer " + acquirer.code() + " references unknown issuer: " + rule.issuerCode());
        }
        return issuer;
    }
}
