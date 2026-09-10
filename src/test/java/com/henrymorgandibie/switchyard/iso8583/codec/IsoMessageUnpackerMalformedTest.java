package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.exception.FieldFormatException;
import com.henrymorgandibie.switchyard.iso8583.exception.MalformedIsoMessageException;
import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedDataElementException;
import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedMtiException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every malformed/truncated/unsupported input case the unpacker must reject with a specific,
 * well-typed exception rather than an unrelated array-index exception or silent misparse.
 */
class IsoMessageUnpackerMalformedTest {

    @Test
    void emptyMessageIsTruncatedBeforeMti() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(new byte[0]))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("MTI");
    }

    @Test
    void tooShortForMtiIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("02")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("MTI");
    }

    @Test
    void nonDigitMtiIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("02A0")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("4 digits");
    }

    @Test
    void unsupportedMtiIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("9999")))
                .isInstanceOf(UnsupportedMtiException.class);
    }

    @Test
    void bitmapTruncatedAfterMtiIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "7220")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("primary bitmap");
    }

    @Test
    void nonHexCharacterInBitmapIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "72200001ZZ808000")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("non-hex");
    }

    @Test
    void bitmapReferencingUndefinedDataElementIsRejected() {
        // bit 5 set (byte0, position 5 -> 2^(7-4)=8 = 0x08); DE5 has no profile definition.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "0800000000000000")))
                .isInstanceOf(UnsupportedDataElementException.class)
                .hasMessageContaining("DE5");
    }

    @Test
    void secondaryBitmapIndicatedButTruncatedIsRejected() {
        // bit 1 set (0x80) forces a secondary bitmap that is then never supplied.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "8000000000000000")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("secondary bitmap");
    }

    @Test
    void llvarLengthPrefixNonNumericIsRejected() {
        // bit 2 set (DE2, LLVAR): byte0 position2 -> 2^6=64 = 0x40.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "4000000000000000" + "1A")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("length prefix");
    }

    @Test
    void llvarDeclaredLengthExceedsProfileMaxIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "4000000000000000" + "99")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("exceeding profile max");
    }

    @Test
    void llvarValueTruncatedBeforeDeclaredLengthIsRejected() {
        // declares 5 bytes for DE2 but only supplies 3.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "4000000000000000" + "05" + "123")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("DE2");
    }

    @Test
    void zeroLengthLlvarValueIsRejected() {
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "4000000000000000" + "00")))
                .isInstanceOf(FieldFormatException.class);
    }

    @Test
    void lllvarLengthPrefixNonNumericIsRejected() {
        // bit 55 set only (DE55, LLLVAR): byte6 position55 -> pos6 -> 2^(7-6)=2 = 0x02; all other bytes 0x00.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "0000000000000200" + "AAA")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("length prefix");
    }

    @Test
    void fixedFieldTruncatedIsRejected() {
        // bit 11 set (DE11, FIXED length 6): byte1 position11 -> pos2 -> 2^5=32 = 0x20.
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(ascii("0200" + "0020000000000000" + "123")))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("DE11");
    }

    @Test
    void trailingBytesAfterLastFieldAreRejected() {
        // Fixture-1-equivalent complete message with one extra trailing byte appended.
        byte[] complete = ascii("0200" + "7220000100808000"
                + "16" + "4111111111111111" + "000000" + "000000005000" + "0910120000" + "000001"
                + "05" + "12345" + "TERM0001" + "566" + "X");
        assertThatThrownBy(() -> IsoMessageUnpacker.unpack(complete))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("trailing bytes");
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
