package com.henrymorgandibie.switchyard.iso8583.message;

import com.henrymorgandibie.switchyard.iso8583.exception.FieldFormatException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IsoMessageBuilderTest {

    @Test
    void fixedNumericFieldIsLeftZeroPaddedWhenShorter() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(11, "1").build();
        assertThat(message.stringField(11)).isEqualTo("000001");
    }

    @Test
    void fixedNumericFieldRejectsNonDigitCharacters() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(3, "12A456"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("digits");
    }

    @Test
    void fixedNumericFieldRejectsValueLongerThanDeclaredLength() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(3, "1234567"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("exactly 6");
    }

    @Test
    void fixedAnsFieldRequiresExactLengthNoImplicitPadding() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).ans(41, "SHORT"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("exactly 8");
    }

    @Test
    void ansFieldAcceptsExactLengthPrintableAscii() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST).ans(41, "TERM0001").build();
        assertThat(message.stringField(41)).isEqualTo("TERM0001");
    }

    @Test
    void ansFieldRejectsNonPrintableCharacter() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).ans(41, "TERM000"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("printable ASCII");
    }

    @Test
    void track2FieldAcceptsDigitsEqualsAndD() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .track2(35, "4111111111111111=2912101").build();
        assertThat(message.stringField(35)).isEqualTo("4111111111111111=2912101");
    }

    @Test
    void track2FieldRejectsCharacterOutsideTrackCharset() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).track2(35, "4111111111111111?2912"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("track 2 charset");
    }

    @Test
    void binaryFixedFieldRequiresExactByteLength() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).binary(52, new byte[]{1, 2, 3}))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("exactly 8 bytes");
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .binary(52, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}).build();
        assertThat(message.rawField(52)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    void binaryVariableFieldRejectsValueExceedingMaxLength() {
        byte[] tooLong = new byte[256];
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).binary(55, tooLong))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("1-255 bytes");
    }

    @Test
    void llvarFieldAcceptsExactlyMaxLength() {
        String nineteenDigits = "1".repeat(19);
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(2, nineteenDigits).build();
        assertThat(message.stringField(2)).isEqualTo(nineteenDigits);
    }

    @Test
    void llvarFieldRejectsOneCharacterOverMaxLength() {
        String twentyDigits = "1".repeat(20);
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(2, twentyDigits))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("exceeds max length 19");
    }

    @Test
    void llvarFieldRejectsEmptyValue() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(2, ""))
                .isInstanceOf(FieldFormatException.class);
    }

    @Test
    void callingWrongTypedSetterForADataElementIsRejected() {
        // DE52 is BINARY - calling the numeric() setter on it must fail, not silently succeed.
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(52, "12345678"))
                .isInstanceOf(FieldFormatException.class)
                .hasMessageContaining("is type BINARY, not NUMERIC");
    }

    @Test
    void undefinedDataElementIsRejectedAtBuildTime() {
        assertThatThrownBy(() -> IsoMessage.builder(Mti.FINANCIAL_REQUEST).numeric(5, "1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rawFieldReturnsDefensiveCopyNotInternalArray() {
        IsoMessage message = IsoMessage.builder(Mti.FINANCIAL_REQUEST)
                .binary(52, new byte[]{1, 2, 3, 4, 5, 6, 7, 8}).build();
        byte[] first = message.rawField(52);
        first[0] = 99;
        assertThat(message.rawField(52)[0]).isEqualTo((byte) 1);
    }
}
