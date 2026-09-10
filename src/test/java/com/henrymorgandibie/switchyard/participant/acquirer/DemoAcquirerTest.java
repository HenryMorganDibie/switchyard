package com.henrymorgandibie.switchyard.participant.acquirer;

import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoAcquirerTest {

    private static final NetworkParticipant ACQUIRER =
            new NetworkParticipant("ACQ-A", "Demo Acquirer A", NetworkParticipant.Role.ACQUIRER);

    @Test
    void resolvesAKnownInstitutionId() {
        DemoAcquirer acquirer = new DemoAcquirer(Map.of("12345", ACQUIRER));

        assertThat(acquirer.resolve("12345")).contains(ACQUIRER);
    }

    @Test
    void unknownInstitutionIdResolvesToEmpty() {
        DemoAcquirer acquirer = new DemoAcquirer(Map.of("12345", ACQUIRER));

        assertThat(acquirer.resolve("99999")).isEmpty();
    }

    @Test
    void rejectsANonAcquirerParticipantInTheRegistry() {
        NetworkParticipant issuer = new NetworkParticipant("ISS-A", "Demo Issuer A", NetworkParticipant.Role.ISSUER);
        assertThatThrownBy(() -> new DemoAcquirer(Map.of("12345", issuer)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACQUIRER");
    }
}
