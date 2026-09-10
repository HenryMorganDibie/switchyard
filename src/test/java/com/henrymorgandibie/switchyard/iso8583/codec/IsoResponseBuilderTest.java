package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsoResponseBuilderTest {

    @Test
    void responseEchoesRequestFieldsAndSetsResponseCode() {
        IsoMessage request = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(2, "4111111111111111")
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        IsoMessage response = IsoResponseBuilder.buildResponse(request, Mti.FINANCIAL_RESPONSE, "00");

        assertThat(response.mti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
        assertThat(response.stringField(2)).isEqualTo(request.stringField(2));
        assertThat(response.stringField(3)).isEqualTo(request.stringField(3));
        assertThat(response.stringField(4)).isEqualTo(request.stringField(4));
        assertThat(response.stringField(7)).isEqualTo(request.stringField(7));
        assertThat(response.stringField(11)).isEqualTo(request.stringField(11));
        assertThat(response.stringField(32)).isEqualTo(request.stringField(32));
        assertThat(response.stringField(41)).isEqualTo(request.stringField(41));
        assertThat(response.stringField(49)).isEqualTo(request.stringField(49));
        assertThat(response.stringField(39)).isEqualTo("00");
        // The response builder only echoes + sets DE39 - it does not itself decide to add an
        // authorization identification response; that is a transaction-pipeline decision.
        assertThat(response.hasField(38)).isFalse();
    }

    @Test
    void pinDataIsNeverEchoedOntoTheResponse() {
        IsoMessage request = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(11, "000001")
                .binary(52, new byte[]{1, 2, 3, 4, 5, 6, 7, 8})
                .build();

        IsoMessage response = IsoResponseBuilder.buildResponse(request, Mti.FINANCIAL_RESPONSE, "00");

        assertThat(response.hasField(52)).isFalse();
        assertThat(response.stringField(11)).isEqualTo("000001");
    }

    @Test
    void approvalWithAuthorizationIdSetsDe38() {
        IsoMessage request = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(11, "000001")
                .build();

        IsoMessage response = IsoResponseBuilder.buildResponse(request, Mti.FINANCIAL_RESPONSE, "00", "AUTH01");

        assertThat(response.stringField(38)).isEqualTo("AUTH01");
        assertThat(response.stringField(39)).isEqualTo("00");
    }

    @Test
    void declinedResponseCarriesTheGivenResponseCode() {
        IsoMessage request = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(11, "000001")
                .build();

        IsoMessage response = IsoResponseBuilder.buildResponse(request, Mti.FINANCIAL_RESPONSE, "51");

        assertThat(response.stringField(39)).isEqualTo("51");
    }
}
