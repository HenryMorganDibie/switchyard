package com.henrymorgandibie.switchyard.idempotency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyKeysTest {

    @Test
    void isDeterministicForTheSameInputs() {
        String first = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 5000L);
        String second = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 5000L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void isASixtyFourCharacterHexString() {
        String key = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 5000L);

        assertThat(key).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void twoDistinctTransactionsSharingTheSameStanProduceDifferentKeys() {
        // Same STAN, different transmission time - a legitimate, distinct occurrence of that STAN.
        String first = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 5000L);
        String second = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910121500", "000000", 5000L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void differingAmountProducesADifferentKey() {
        String first = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 5000L);
        String second = IdempotencyKeys.compute("12345", "TERM0001", "000001", "0910120000", "000000", 6000L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void nullFieldsAreHandledWithoutThrowing() {
        String key = IdempotencyKeys.compute(null, null, "000001", "0910120000", null, 5000L);

        assertThat(key).hasSize(64);
    }
}
