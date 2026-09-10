package com.henrymorgandibie.switchyard.iso8583.validation;

import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedMtiException;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtiValidatorTest {

    @Test
    void acceptsAllEightSupportedMtis() {
        assertThat(MtiValidator.validate("0100")).isEqualTo(Mti.AUTHORIZATION_REQUEST);
        assertThat(MtiValidator.validate("0110")).isEqualTo(Mti.AUTHORIZATION_RESPONSE);
        assertThat(MtiValidator.validate("0200")).isEqualTo(Mti.FINANCIAL_REQUEST);
        assertThat(MtiValidator.validate("0210")).isEqualTo(Mti.FINANCIAL_RESPONSE);
        assertThat(MtiValidator.validate("0400")).isEqualTo(Mti.REVERSAL_REQUEST);
        assertThat(MtiValidator.validate("0410")).isEqualTo(Mti.REVERSAL_RESPONSE);
        assertThat(MtiValidator.validate("0800")).isEqualTo(Mti.NETWORK_MANAGEMENT_REQUEST);
        assertThat(MtiValidator.validate("0810")).isEqualTo(Mti.NETWORK_MANAGEMENT_RESPONSE);
    }

    @Test
    void rejectsUnknownMti() {
        assertThatThrownBy(() -> MtiValidator.validate("1200"))
                .isInstanceOf(UnsupportedMtiException.class);
    }
}
