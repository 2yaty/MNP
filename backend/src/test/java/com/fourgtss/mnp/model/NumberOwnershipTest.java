package com.fourgtss.mnp.model;

import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberOwnershipTest {

    @Test
    @DisplayName("US-A1-AC1 a number with its range owner isn't ported")
    void numberWithRangeOwnerIsNotPorted() {
        assertThat(new NumberOwnership(VODAFONE, VODAFONE).isPorted()).isFalse();
    }

    @Test
    @DisplayName("US-A1-AC2 a number with another operator is ported")
    void numberWithAnotherOperatorIsPorted() {
        assertThat(new NumberOwnership(VODAFONE, ORANGE).isPorted()).isTrue();
    }
}
