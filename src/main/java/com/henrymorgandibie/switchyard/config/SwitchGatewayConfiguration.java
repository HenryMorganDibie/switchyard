package com.henrymorgandibie.switchyard.config;

import com.henrymorgandibie.switchyard.network.tcp.IsoTcpServer;
import com.henrymorgandibie.switchyard.network.tcp.IsoTcpServerConfig;
import com.henrymorgandibie.switchyard.participant.acquirer.AcquirerConnector;
import com.henrymorgandibie.switchyard.participant.acquirer.DemoAcquirer;
import com.henrymorgandibie.switchyard.participant.issuer.DemoIssuer;
import com.henrymorgandibie.switchyard.participant.issuer.IssuerConnector;
import com.henrymorgandibie.switchyard.routing.application.DefaultTransactionRouter;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import com.henrymorgandibie.switchyard.routing.domain.RoutingRule;
import com.henrymorgandibie.switchyard.routing.domain.TransactionRouter;
import com.henrymorgandibie.switchyard.transaction.application.TransactionProcessingPipeline;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionEventRepository;
import com.henrymorgandibie.switchyard.transaction.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/**
 * The composition root wiring this milestone's pieces (codec, TCP gateway, transaction domain,
 * state machine, routing, simulators) into one running switch. Demo participants and the
 * routing rule between them are hardcoded here rather than loaded from configuration - this
 * reference deployment has exactly one acquirer and one issuer, both simulated; a real
 * deployment's participant/rule set would come from wherever the admin API milestone decides to
 * source it from.
 *
 * <p>The TCP gateway is disabled by default in tests (see {@code src/test/resources/
 * application.yml}) so the many existing Spring context tests that have nothing to do with the
 * network layer don't each start a real listening socket; it defaults to enabled for
 * {@code bootRun} and the one test that specifically exercises the gateway re-enables it.
 */
@Configuration
public class SwitchGatewayConfiguration {

    @Bean
    public NetworkParticipant demoAcquirerParticipant() {
        return new NetworkParticipant("ACQ-DEMO", "DemoAcquirer", NetworkParticipant.Role.ACQUIRER);
    }

    @Bean
    public NetworkParticipant demoIssuerParticipant() {
        return new NetworkParticipant("ISS-DEMO", "DemoIssuer", NetworkParticipant.Role.ISSUER);
    }

    @Bean
    public AcquirerConnector acquirerConnector(NetworkParticipant demoAcquirerParticipant) {
        // "12345" matches the acquiring institution id used throughout this project's ISO 8583
        // test fixtures.
        return new DemoAcquirer(Map.of("12345", demoAcquirerParticipant));
    }

    @Bean
    public IssuerConnector demoIssuer(NetworkParticipant demoIssuerParticipant) {
        return new DemoIssuer(demoIssuerParticipant);
    }

    @Bean
    public TransactionRouter transactionRouter(AcquirerConnector acquirerConnector, IssuerConnector demoIssuer,
                                                 NetworkParticipant demoAcquirerParticipant,
                                                 NetworkParticipant demoIssuerParticipant) {
        return new DefaultTransactionRouter(
                acquirerConnector,
                Map.of(demoIssuerParticipant.code(), demoIssuer),
                List.of(new RoutingRule(demoAcquirerParticipant.code(), demoIssuerParticipant.code(), 1, true)));
    }

    @Bean
    public TransactionProcessingPipeline transactionProcessingPipeline(TransactionRepository transactionRepository,
                                                                         TransactionEventRepository eventRepository,
                                                                         TransactionRouter router) {
        return new TransactionProcessingPipeline(transactionRepository, eventRepository, router);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "switchyard.tcp", name = "enabled", havingValue = "true", matchIfMissing = true)
    public IsoTcpServer isoTcpServer(TransactionProcessingPipeline pipeline,
                                      @Value("${switchyard.tcp.port:8583}") int port) {
        return new IsoTcpServer(IsoTcpServerConfig.defaults(port), pipeline);
    }
}
