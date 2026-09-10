package com.henrymorgandibie.switchyard.iso8583.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtiTest {

    @Test
    void everyRequestMtiMapsToItsResponseMti() {
        assertThat(Mti.AUTHORIZATION_REQUEST.responseMti()).isEqualTo(Mti.AUTHORIZATION_RESPONSE);
        assertThat(Mti.FINANCIAL_REQUEST.responseMti()).isEqualTo(Mti.FINANCIAL_RESPONSE);
        assertThat(Mti.REVERSAL_REQUEST.responseMti()).isEqualTo(Mti.REVERSAL_RESPONSE);
        assertThat(Mti.NETWORK_MANAGEMENT_REQUEST.responseMti()).isEqualTo(Mti.NETWORK_MANAGEMENT_RESPONSE);
    }

    @Test
    void aResponseMtiHasNoResponseMtiOfItsOwn() {
        assertThatThrownBy(Mti.FINANCIAL_RESPONSE::responseMti).isInstanceOf(IllegalStateException.class);
    }
}
