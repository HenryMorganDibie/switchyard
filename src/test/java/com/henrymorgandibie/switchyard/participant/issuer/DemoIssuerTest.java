package com.henrymorgandibie.switchyard.participant.issuer;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.routing.domain.NetworkParticipant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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

    @Test
    void requestWithNoPanDefaultsToApprove() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequest());
        assertThat(response.approved()).isTrue();
    }

    @Test
    void unrecognizedPanDefaultsToApprove() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4999999999999999"));
        assertThat(response.approved()).isTrue();
    }

    @Test
    void declineTestPanReturnsResponseCode05() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4000000000000005"));
        assertThat(response.responseCode()).isEqualTo("05");
        assertThat(response.approved()).isFalse();
    }

    @Test
    void insufficientFundsTestPanReturnsResponseCode51() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4000000000000051"));
        assertThat(response.responseCode()).isEqualTo("51");
    }

    @Test
    void invalidAccountTestPanReturnsResponseCode14() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4000000000000014"));
        assertThat(response.responseCode()).isEqualTo("14");
    }

    @Test
    void issuerUnavailableTestPanThrows() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        assertThatThrownBy(() -> issuer.authorize(sampleRequestWithPan("4000000000000091")))
                .isInstanceOf(IssuerUnavailableException.class);
    }

    @Test
    void connectionResetTestPanThrows() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        assertThatThrownBy(() -> issuer.authorize(sampleRequestWithPan("4000000000000096")))
                .isInstanceOf(IssuerConnectionResetException.class);
    }

    @Test
    void malformedResponseTestPanReturnsAnUnparseableResponseCode() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4000000000000003"));
        assertThat(response.responseCode()).isNotEqualTo("00");
        assertThat(response.responseCode()).doesNotMatch("\\d{2}");
    }

    @Test
    @Timeout(10)
    void delayedApprovalTestPanEventuallyApprovesAfterAMeasurableDelay() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        long start = System.nanoTime();
        IssuerResponse response = issuer.authorize(sampleRequestWithPan("4000000000000002"));
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertThat(response.approved()).isTrue();
        assertThat(elapsedMillis).isGreaterThanOrEqualTo(100);
    }

    @Test
    @Timeout(10)
    void timeoutTestPanBlocksLongerThanADelayedApproval() {
        DemoIssuer issuer = new DemoIssuer(ISSUER);
        long delayedStart = System.nanoTime();
        issuer.authorize(sampleRequestWithPan("4000000000000002"));
        long delayedElapsedMillis = (System.nanoTime() - delayedStart) / 1_000_000;

        long timeoutStart = System.nanoTime();
        issuer.authorize(sampleRequestWithPan("4000000000000001"));
        long timeoutElapsedMillis = (System.nanoTime() - timeoutStart) / 1_000_000;

        assertThat(timeoutElapsedMillis).isGreaterThan(delayedElapsedMillis);
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

    private static IsoMessage sampleRequestWithPan(String pan) {
        return IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(2, pan)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();
    }
}
