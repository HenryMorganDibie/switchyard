package com.henrymorgandibie.switchyard.participant.simulator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaultInjectionRegistryTest {

    @Test
    void nullPanDefaultsToApprove() {
        assertThat(FaultInjectionRegistry.resolve(null)).isEqualTo(FaultScenario.APPROVE);
    }

    @Test
    void unrecognizedPanDefaultsToApprove() {
        assertThat(FaultInjectionRegistry.resolve("4999999999999999")).isEqualTo(FaultScenario.APPROVE);
    }

    @Test
    void everyDocumentedTestPanResolvesToItsScenario() {
        assertThat(FaultInjectionRegistry.resolve("4111111111111111")).isEqualTo(FaultScenario.APPROVE);
        assertThat(FaultInjectionRegistry.resolve("4000000000000005")).isEqualTo(FaultScenario.DECLINE);
        assertThat(FaultInjectionRegistry.resolve("4000000000000051")).isEqualTo(FaultScenario.INSUFFICIENT_FUNDS);
        assertThat(FaultInjectionRegistry.resolve("4000000000000014")).isEqualTo(FaultScenario.INVALID_ACCOUNT);
        assertThat(FaultInjectionRegistry.resolve("4000000000000091")).isEqualTo(FaultScenario.ISSUER_UNAVAILABLE);
        assertThat(FaultInjectionRegistry.resolve("4000000000000096")).isEqualTo(FaultScenario.CONNECTION_RESET);
        assertThat(FaultInjectionRegistry.resolve("4000000000000001")).isEqualTo(FaultScenario.TIMEOUT);
        assertThat(FaultInjectionRegistry.resolve("4000000000000002")).isEqualTo(FaultScenario.DELAYED_APPROVAL);
        assertThat(FaultInjectionRegistry.resolve("4000000000000003")).isEqualTo(FaultScenario.MALFORMED_RESPONSE);
    }
}
