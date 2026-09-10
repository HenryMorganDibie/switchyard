package com.henrymorgandibie.switchyard.iso8583.profile;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Iso8583ProfileTest {

    @Test
    void definesExactlyTheProfiledDataElements() {
        assertThat(Iso8583Profile.all().keySet()).containsExactly(
                2, 3, 4, 7, 11, 12, 13, 18, 22, 25, 32, 35, 37, 38, 39, 41, 42, 43, 49, 52, 55, 70);
    }

    @Test
    void panIsLlvarNumericMax19() {
        DataElementDefinition de2 = Iso8583Profile.require(2);
        assertThat(de2.type()).isEqualTo(FieldType.NUMERIC);
        assertThat(de2.lengthType()).isEqualTo(LengthType.LLVAR);
        assertThat(de2.length()).isEqualTo(19);
        assertThat(de2.lengthPrefixDigits()).isEqualTo(2);
    }

    @Test
    void processingCodeIsFixedNumericSix() {
        DataElementDefinition de3 = Iso8583Profile.require(3);
        assertThat(de3.type()).isEqualTo(FieldType.NUMERIC);
        assertThat(de3.lengthType()).isEqualTo(LengthType.FIXED);
        assertThat(de3.length()).isEqualTo(6);
        assertThat(de3.lengthPrefixDigits()).isEqualTo(0);
    }

    @Test
    void iccDataIsBinaryLllvar() {
        DataElementDefinition de55 = Iso8583Profile.require(55);
        assertThat(de55.type()).isEqualTo(FieldType.BINARY);
        assertThat(de55.lengthType()).isEqualTo(LengthType.LLLVAR);
        assertThat(de55.lengthPrefixDigits()).isEqualTo(3);
    }

    @Test
    void pinDataIsFixedBinaryEightBytes() {
        DataElementDefinition de52 = Iso8583Profile.require(52);
        assertThat(de52.type()).isEqualTo(FieldType.BINARY);
        assertThat(de52.lengthType()).isEqualTo(LengthType.FIXED);
        assertThat(de52.length()).isEqualTo(8);
    }

    @Test
    void track2IsLlvarWithTrackCharset() {
        DataElementDefinition de35 = Iso8583Profile.require(35);
        assertThat(de35.type()).isEqualTo(FieldType.TRACK2);
        assertThat(de35.lengthType()).isEqualTo(LengthType.LLVAR);
        assertThat(de35.length()).isEqualTo(37);
    }

    @Test
    void undefinedDataElementIsRejected() {
        assertThat(Iso8583Profile.isDefined(5)).isFalse();
        assertThatThrownBy(() -> Iso8583Profile.require(5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definedDataElementIsAccepted() {
        assertThat(Iso8583Profile.isDefined(41)).isTrue();
    }
}
