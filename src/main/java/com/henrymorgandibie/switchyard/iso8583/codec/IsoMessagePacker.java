package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.exception.IsoMessageException;
import com.henrymorgandibie.switchyard.iso8583.message.IsoMessage;
import com.henrymorgandibie.switchyard.iso8583.profile.DataElementDefinition;
import com.henrymorgandibie.switchyard.iso8583.profile.Iso8583Profile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeSet;

/**
 * Packs an {@link IsoMessage} into switchyard's wire representation: 4-byte ASCII MTI, a
 * hex-ASCII primary bitmap, a hex-ASCII secondary bitmap when any DE 65-128 is present, then
 * each present data element in ascending DE number order (LLVAR/LLLVAR fields prefixed with
 * their ASCII length). See {@code docs/iso8583.md} for the full profile and wire-format
 * rationale.
 *
 * <p>Stateless and thread-safe: holds no mutable state, safe to share across threads.
 */
public final class IsoMessagePacker {

    private IsoMessagePacker() {
    }

    public static byte[] pack(IsoMessage message) {
        SortedMap<Integer, byte[]> fields = message.fields();

        Set<Integer> primaryPositions = new TreeSet<>();
        Set<Integer> secondaryPositions = new TreeSet<>();
        for (int de : fields.keySet()) {
            if (de <= 64) {
                primaryPositions.add(de);
            } else {
                secondaryPositions.add(de - 64);
            }
        }
        boolean hasSecondary = !secondaryPositions.isEmpty();
        if (hasSecondary) {
            primaryPositions.add(1);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeAscii(out, message.mti().code());
        out.writeBytes(BitmapCodec.toHexAscii(BitmapCodec.packWord(primaryPositions)));
        if (hasSecondary) {
            out.writeBytes(BitmapCodec.toHexAscii(BitmapCodec.packWord(secondaryPositions)));
        }

        for (Map.Entry<Integer, byte[]> entry : fields.entrySet()) {
            int de = entry.getKey();
            byte[] value = entry.getValue();
            DataElementDefinition definition = Iso8583Profile.require(de);
            writeLengthPrefix(out, definition, value.length);
            out.writeBytes(value);
        }

        return out.toByteArray();
    }

    private static void writeLengthPrefix(ByteArrayOutputStream out, DataElementDefinition definition,
                                           int actualLength) {
        int prefixDigits = definition.lengthPrefixDigits();
        if (prefixDigits == 0) {
            if (actualLength != definition.length()) {
                throw new IsoMessageException("DE" + definition.number() + " (" + definition.name()
                        + ") value length " + actualLength + " does not match fixed length " + definition.length());
            }
            return;
        }
        if (actualLength > definition.length()) {
            throw new IsoMessageException("DE" + definition.number() + " (" + definition.name()
                    + ") value length " + actualLength + " exceeds max " + definition.length());
        }
        String prefix = Integer.toString(actualLength);
        prefix = "0".repeat(prefixDigits - prefix.length()) + prefix;
        writeAscii(out, prefix);
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        out.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
