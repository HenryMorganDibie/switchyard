package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.exception.MalformedIsoMessageException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Pure bit-math for a single 64-bit ISO 8583 bitmap word: packing a set of 1-based positions
 * (1-64) into 8 raw bytes, hex-ASCII encoding those raw bytes for the wire (switchyard's
 * profile uses a hex-ASCII bitmap representation, not raw binary), and the reverse.
 *
 * <p>Has no knowledge of the data element profile or of primary vs. secondary bitmaps -
 * {@link IsoMessagePacker}/{@link IsoMessageUnpacker} translate between DE numbers and
 * (bitmap word, 1-64 position) themselves. Stateless and thread-safe.
 */
final class BitmapCodec {

    private static final int WORD_BITS = 64;
    private static final int WORD_BYTES = 8;
    private static final int HEX_CHARS = 16;

    private BitmapCodec() {
    }

    /** Packs 1-based bit positions (1-64) into 8 raw bitmap bytes, MSB-first (bit 1 = MSB of byte 0). */
    static byte[] packWord(Set<Integer> positions) {
        byte[] raw = new byte[WORD_BYTES];
        for (int position : positions) {
            if (position < 1 || position > WORD_BITS) {
                throw new IllegalArgumentException("bitmap position out of range [1," + WORD_BITS + "]: " + position);
            }
            int byteIndex = (position - 1) / 8;
            int bitInByte = (position - 1) % 8;
            raw[byteIndex] |= (byte) (0x80 >>> bitInByte);
        }
        return raw;
    }

    /** Unpacks 8 raw bitmap bytes into the set of 1-based positions (1-64) that are set. */
    static SortedSet<Integer> unpackWord(byte[] raw) {
        if (raw.length != WORD_BYTES) {
            throw new IllegalArgumentException("raw bitmap word must be " + WORD_BYTES + " bytes, got " + raw.length);
        }
        SortedSet<Integer> positions = new TreeSet<>();
        for (int byteIndex = 0; byteIndex < WORD_BYTES; byteIndex++) {
            int b = raw[byteIndex] & 0xFF;
            for (int bitInByte = 0; bitInByte < 8; bitInByte++) {
                if ((b & (0x80 >>> bitInByte)) != 0) {
                    positions.add(byteIndex * 8 + bitInByte + 1);
                }
            }
        }
        return positions;
    }

    /** Hex-ASCII encodes 8 raw bytes into 16 upper-case ASCII bytes for the wire. */
    static byte[] toHexAscii(byte[] raw) {
        if (raw.length != WORD_BYTES) {
            throw new IllegalArgumentException("raw bitmap word must be " + WORD_BYTES + " bytes, got " + raw.length);
        }
        char[] hex = new char[HEX_CHARS];
        for (int i = 0; i < WORD_BYTES; i++) {
            int b = raw[i] & 0xFF;
            hex[i * 2] = Character.forDigit((b >>> 4) & 0xF, 16);
            hex[i * 2 + 1] = Character.forDigit(b & 0xF, 16);
        }
        return new String(hex).toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
    }

    /** Decodes 16 ASCII hex bytes from the wire back into 8 raw bitmap bytes. */
    static byte[] fromHexAscii(byte[] hexAscii) {
        if (hexAscii.length != HEX_CHARS) {
            throw new MalformedIsoMessageException(
                    "bitmap must be " + HEX_CHARS + " hex-ASCII bytes, got " + hexAscii.length);
        }
        byte[] raw = new byte[WORD_BYTES];
        for (int i = 0; i < WORD_BYTES; i++) {
            int hi = hexDigit(hexAscii[i * 2]);
            int lo = hexDigit(hexAscii[i * 2 + 1]);
            raw[i] = (byte) ((hi << 4) | lo);
        }
        return raw;
    }

    private static int hexDigit(byte b) {
        int digit = Character.digit(b, 16);
        if (digit < 0) {
            throw new MalformedIsoMessageException(
                    "bitmap contains a non-hex character: 0x" + Integer.toHexString(b & 0xFF));
        }
        return digit;
    }
}
