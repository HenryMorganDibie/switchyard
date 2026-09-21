package com.henrymorgandibie.switchyard.routing.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NetworkParticipantStatusRegistryTest {

    @Test
    void anInstitutionNeverSeenIsTreatedAsDown() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();

        assertThat(registry.statusOf("12345")).isEqualTo(ParticipantStatus.DOWN);
    }

    @Test
    void markUpThenStatusOfReflectsUp() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();

        registry.markUp("12345");

        assertThat(registry.statusOf("12345")).isEqualTo(ParticipantStatus.UP);
    }

    @Test
    void markDownAfterMarkUpReturnsToDown() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();

        registry.markUp("12345");
        registry.markDown("12345");

        assertThat(registry.statusOf("12345")).isEqualTo(ParticipantStatus.DOWN);
    }

    @Test
    void statusIsTrackedIndependentlyPerInstitution() {
        NetworkParticipantStatusRegistry registry = new NetworkParticipantStatusRegistry();

        registry.markUp("12345");

        assertThat(registry.statusOf("12345")).isEqualTo(ParticipantStatus.UP);
        assertThat(registry.statusOf("99001")).isEqualTo(ParticipantStatus.DOWN);
    }
}
