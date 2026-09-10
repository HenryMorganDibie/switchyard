package com.henrymorgandibie.switchyard.iso8583.validation;

import com.henrymorgandibie.switchyard.iso8583.exception.RequiredFieldMissingException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredFieldsValidatorTest {

    @Test
    void financialRequestMissingAmountIsRejected() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        assertThatThrownBy(() -> RequiredFieldsValidator.validate(message))
                .isInstanceOf(RequiredFieldMissingException.class)
                .hasMessageContaining("DE4");
    }

    @Test
    void financialRequestWithAllRequiredFieldsPasses() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        assertThatCode(() -> RequiredFieldsValidator.validate(message)).doesNotThrowAnyException();
    }

    @Test
    void financialRequestMissingAcquiringInstitutionIsRejected() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        assertThatThrownBy(() -> RequiredFieldsValidator.validate(message))
                .isInstanceOf(RequiredFieldMissingException.class)
                .hasMessageContaining("DE32");
    }

    @Test
    void networkManagementRequestOnlyNeedsTransmissionTimeAndStan() {
        IsoMessage message = IsoMessage.builder(Mti.NETWORK_MANAGEMENT_REQUEST)
                .numeric(7, "0910120700")
                .numeric(11, "000004")
                .build();

        assertThatCode(() -> RequiredFieldsValidator.validate(message)).doesNotThrowAnyException();
    }

    @Test
    void financialResponseMissingResponseCodeIsRejected() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_RESPONSE)
                .numeric(3, "000000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120000")
                .numeric(11, "000001")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        assertThatThrownBy(() -> RequiredFieldsValidator.validate(message))
                .isInstanceOf(RequiredFieldMissingException.class)
                .hasMessageContaining("DE39");
    }

    @Test
    void reversalRequestMissingRrnIsRejected() {
        IsoMessage message = IsoMessage.builder(Mti.REVERSAL_REQUEST)
                .numeric(3, "020000")
                .numeric(4, "000000005000")
                .numeric(7, "0910120600")
                .numeric(11, "000003")
                .numeric(32, "12345")
                .ans(41, "TERM0001")
                .numeric(49, "566")
                .build();

        assertThatThrownBy(() -> RequiredFieldsValidator.validate(message))
                .isInstanceOf(RequiredFieldMissingException.class)
                .hasMessageContaining("DE37");
    }
}
