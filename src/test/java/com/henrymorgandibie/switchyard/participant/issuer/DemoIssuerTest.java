package com.henrymorgandibie.switchyard.participant.issuer;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoIssuerTest {

    private static final NetworkParticipant ISSUER =
            new NetworkParticipant("ISS-A", "Demo Issuer A", NetworkParticipant.Role.ISSUER);

    @Test
    void alwaysApproves() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequest());

        assertThat(response.approved()).isTrue();
        assertThat(response.responseCode()).isEqualTo("00");
    }

    @Test
    void generatesASixCharacterAlphanumericAuthorizationId() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequest());

        assertThat(response.authorizationId()).matches("[A-Z0-9]{6}");
    }

    @Test
    void authorizationIdsAreNotAllIdenticalAcrossCalls() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        var first = issuer.authorize(sampleRequest()).authorizationId();
        var distinctSeen = false;
        for (int i = 0; i < 20; i++) {
            if (!issuer.authorize(sampleRequest()).authorizationId().equals(first)) {
                distinctSeen = true;
                break;
            }
        }
        assertThat(distinctSeen).as("expected at least one different id across 20 calls").isTrue();
    }

    @Test
    void exposesItsParticipant() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        assertThat(issuer.participant()).isEqualTo(ISSUER);
    }

    @Test
    void rejectsANonIssuerParticipant() {
        NetworkParticipant acquirer = new NetworkParticipant("ACQ-A", "Demo Acquirer A", NetworkParticipant.Role.ACQUIRER);
        assertThatThrownBy(() -> new DemoIssuer(acquirer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISSUER");
    }

    private static IsoMessage sampleRequest() {
        return IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
    }
}
