package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.exception.MalformedIsoMessageException;
import com.henrymorgandibie.switchyard.iso8583.exception.UnsupportedDataElementException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.message.Mti;
import com.henrymorgandibie.switchyard.iso8583.profile.DataElementDefinition;
import com.henrymorgandibie.switchyard.iso8583.profile.Iso8583Profile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Unpacks a switchyard-wire-format ISO 8583 message (see {@link IsoMessagePacker}) back into an
 * {@link IsoMessage}. Every read is bounds-checked against the remaining buffer and fails with
 * {@link MalformedIsoMessageException} identifying exactly where parsing failed, rather than
 * letting a truncated/malformed message surface as an unrelated array-index exception.
 *
 * <p>Stateless and thread-safe.
 */
public final class IsoMessageUnpacker {

    private static final int MTI_LENGTH = 4;
    private static final int BITMAP_HEX_LENGTH = 16;

    private IsoMessageUnpacker() {
    }

    public static IsoMessage unpack(byte[] wire) {
        Cursor cursor = new Cursor(wire);

        String mtiCode = cursor.readAscii(MTI_LENGTH, "MTI");
        if (mtiCode.chars().anyMatch(c -> !Character.isDigit(c))) {
            throw new MalformedIsoMessageException("MTI must be 4 digits, got: " + mtiCode);
        }
        Mti mti = Mti.fromCode(mtiCode);

        byte[] primaryRaw = BitmapCodec.fromHexAscii(cursor.readBytes(BITMAP_HEX_LENGTH, "primary bitmap"));
        SortedSet<Integer> primaryPositions = BitmapCodec.unpackWord(primaryRaw);

        boolean hasSecondary = primaryPositions.contains(1);
        SortedSet<Integer> presentDataElements = new TreeSet<>();
        for (int position : primaryPositions) {
            if (position != 1) {
                presentDataElements.add(position);
            }
        }
        if (hasSecondary) {
            byte[] secondaryRaw = BitmapCodec.fromHexAscii(cursor.readBytes(BITMAP_HEX_LENGTH, "secondary bitmap"));
            for (int position : BitmapCodec.unpackWord(secondaryRaw)) {
                presentDataElements.add(position + 64);
            }
        }

        IsoMessage.Builder builder = IsoMessage.builder(mti);
        for (int de : presentDataElements) {
            if (!Iso8583Profile.isDefined(de)) {
                throw new UnsupportedDataElementException(de);
            }
            DataElementDefinition definition = Iso8583Profile.require(de);
            int length = switch (definition.lengthType()) {
                case FIXED -> definition.length();
                case LLVAR -> cursor.readLengthPrefix(2, definition);
                case LLLVAR -> cursor.readLengthPrefix(3, definition);
            };
            byte[] value = cursor.readBytes(length, "DE" + de + " (" + definition.name() + ")");
            applyField(builder, definition, value);
        }

        cursor.requireExhausted();
        return builder.build();
    }

    private static void applyField(IsoMessage.Builder builder, DataElementDefinition definition, byte[] value) {
        switch (definition.type()) {
            case NUMERIC -> builder.numeric(definition.number(), new String(value, StandardCharsets.US_ASCII));
            case ANS -> builder.ans(definition.number(), new String(value, StandardCharsets.US_ASCII));
            case TRACK2 -> builder.track2(definition.number(), new String(value, StandardCharsets.US_ASCII));
            case BINARY -> builder.binary(definition.number(), value);
        }
    }

    /** Tracks the read position through the wire buffer, bounds-checking every read. */
    private static final class Cursor {

        private final byte[] wire;
        private int position;

        Cursor(byte[] wire) {
            this.wire = wire;
        }

        byte[] readBytes(int length, String what) {
            if (position + length > wire.length) {
                throw new MalformedIsoMessageException("message truncated reading " + what + ": need " + length
                        + " byte(s) at offset " + position + ", only " + (wire.length - position) + " remain");
            }
            byte[] value = Arrays.copyOfRange(wire, position, position + length);
            position += length;
            return value;
        }

        String readAscii(int length, String what) {
            return new String(readBytes(length, what), StandardCharsets.US_ASCII);
        }

        int readLengthPrefix(int digits, DataElementDefinition definition) {
            String prefix = readAscii(digits, "length prefix for DE" + definition.number()
                    + " (" + definition.name() + ")");
            if (prefix.chars().anyMatch(c -> !Character.isDigit(c))) {
                throw new MalformedIsoMessageException("length prefix for DE" + definition.number()
                        + " (" + definition.name() + ") must be " + digits + " digits, got: " + prefix);
            }
            int length = Integer.parseInt(prefix);
            if (length > definition.length()) {
                throw new MalformedIsoMessageException("DE" + definition.number() + " (" + definition.name()
                        + ") declares length " + length + " exceeding profile max " + definition.length());
            }
            return length;
        }

        void requireExhausted() {
            if (position != wire.length) {
                throw new MalformedIsoMessageException("trailing bytes after last expected field: "
                        + (wire.length - position) + " unexpected byte(s) remain");
            }
        }
    }
}
