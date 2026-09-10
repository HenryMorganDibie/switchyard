package com.henrymorgandibie.switchyard.iso8583.codec;

import com.henrymorgandibie.switchyard.iso8583.exception.MalformedIsoMessageException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure bit-math tests for {@link BitmapCodec}, independent of the data element profile.
 * Expected byte values below are computed by hand (bit N = MSB-first position N within its
 * byte, byte index (N-1)/8), not by round-tripping through the codec itself.
 */
class BitmapCodecTest {

    @Test
    void bitOneAloneSetsMsbOfFirstByte() {
        byte[] raw = BitmapCodec.packWord(Set.of(1));
        assertThat(raw).containsExactly(0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void bitSixtyFourAloneSetsLsbOfLastByte() {
        byte[] raw = BitmapCodec.packWord(Set.of(64));
        assertThat(raw).containsExactly(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01);
    }

    @Test
    void emptySetPacksToAllZeroBytes() {
        byte[] raw = BitmapCodec.packWord(Set.of());
        assertThat(raw).containsExactly(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
    }

    @Test
    void allSixtyFourBitsSetPacksToAllOnes() {
        Set<Integer> all = new java.util.TreeSet<>();
        for (int i = 1; i <= 64; i++) {
            all.add(i);
        }
        byte[] raw = BitmapCodec.packWord(all);
        assertThat(raw).containsExactly(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF);
    }

    @Test
    void knownFieldCombinationMatchesHandComputedByte() {
        // DE2,3,4,7 present (byte 0 covers positions 1-8): bit2=64 + bit3=32 + bit4=16 + bit7=2 = 114 = 0x72
        byte[] raw = BitmapCodec.packWord(Set.of(2, 3, 4, 7));
        assertThat(raw[0] & 0xFF).isEqualTo(0x72);
    }

    @Test
    void unpackWordIsTheExactInverseOfHandComputedBytes() {
        // Independently authored raw bytes (not produced by packWord) for positions {2,3,4,7,11,32,41,49}.
        byte[] raw = {0x72, 0x20, 0x00, 0x01, 0x00, (byte) 0x80, (byte) 0x80, 0x00};
        assertThat(BitmapCodec.unpackWord(raw)).containsExactly(2, 3, 4, 7, 11, 32, 41, 49);
    }

    @Test
    void toHexAsciiMatchesHandComputedHexString() {
        byte[] raw = {0x72, 0x20, 0x00, 0x01, 0x00, (byte) 0x80, (byte) 0x80, 0x00};
        assertThat(new String(BitmapCodec.toHexAscii(raw), StandardCharsets.US_ASCII))
                .isEqualTo("7220000100808000");
    }

    @Test
    void fromHexAsciiMatchesHandComputedRawBytes() {
        byte[] raw = BitmapCodec.fromHexAscii("7220000100808000".getBytes(StandardCharsets.US_ASCII));
        assertThat(raw).containsExactly(0x72, 0x20, 0x00, 0x01, 0x00, 0x80, 0x80, 0x00);
    }

    @Test
    void fromHexAsciiRejectsNonHexCharacter() {
        assertThatThrownBy(() -> BitmapCodec.fromHexAscii("72200001ZZ808000".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("non-hex");
    }

    @Test
    void fromHexAsciiRejectsWrongLength() {
        assertThatThrownBy(() -> BitmapCodec.fromHexAscii("7220".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(MalformedIsoMessageException.class)
                .hasMessageContaining("16 hex-ASCII bytes");
    }

    @Test
    void packWordRejectsOutOfRangePosition() {
        assertThatThrownBy(() -> BitmapCodec.packWord(Set.of(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BitmapCodec.packWord(Set.of(0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hexAsciiRoundTripIsConsistentWithIndependentRawBytes() {
        byte[] raw = {(byte) 0x82, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        String hex = new String(BitmapCodec.toHexAscii(raw), StandardCharsets.US_ASCII);
        assertThat(hex).isEqualTo("8220000000000000");
        assertThat(BitmapCodec.fromHexAscii(hex.getBytes(StandardCharsets.US_ASCII))).containsExactly(raw);
    }
}
