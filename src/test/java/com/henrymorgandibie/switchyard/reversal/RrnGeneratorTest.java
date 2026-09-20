package com.henrymorgandibie.switchyard.reversal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RrnGeneratorTest {

    @Test
    void concatenatesTransmissionDateTimeWithTheLastTwoDigitsOfStan() {
        assertThat(RrnGenerator.generate("0910120000", "000123")).isEqualTo("091012000023");
    }

    @Test
    void resultIsAlwaysTwelveCharactersLongMatchingDe37sFixedLength() {
        assertThat(RrnGenerator.generate("0910120000", "000123")).hasSize(12);
    }

    @Test
    void differentStansProduceDifferentRrnsForTheSameTimestamp() {
        String first = RrnGenerator.generate("0910120000", "000001");
        String second = RrnGenerator.generate("0910120000", "000002");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsATransmissionDateTimeThatIsNotTenDigits() {
        assertThatThrownBy(() -> RrnGenerator.generate("091012", "000123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transmissionDateTime");
    }

    @Test
    void rejectsANullTransmissionDateTime() {
        assertThatThrownBy(() -> RrnGenerator.generate(null, "000123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transmissionDateTime");
    }

    @Test
    void rejectsAStanShorterThanTwoCharacters() {
        assertThatThrownBy(() -> RrnGenerator.generate("0910120000", "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stan");
    }

    @Test
    void rejectsANullStan() {
        assertThatThrownBy(() -> RrnGenerator.generate("0910120000", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stan");
    }
}
