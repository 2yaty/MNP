package com.fourgtss.mnp.model;

import static com.fourgtss.mnp.fixture.TestOperators.ETISALAT;
import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class PortingRequestTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortingStatus.class)
    @DisplayName("US-R5-AC1/US-D5-AC1 the recipient and donor see the request in every status")
    void partiesSeeEveryStatus(PortingStatus status) {
        PortingRequest request = orangeRequestingFromVodafone(status);

        assertThat(request.isVisibleTo(ORANGE)).isTrue();
        assertThat(request.isVisibleTo(VODAFONE)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PortingStatus.class)
    @DisplayName("US-T1-AC1 a third party sees only accepted requests")
    void thirdPartySeesOnlyAccepted(PortingStatus status) {
        assertThat(orangeRequestingFromVodafone(status).isVisibleTo(ETISALAT))
                .isEqualTo(status == PortingStatus.ACCEPTED);
    }

    private static PortingRequest orangeRequestingFromVodafone(PortingStatus status) {
        PortingRequest request = PortingRequest.submit(UUID.randomUUID(), "01012345678", ORANGE, VODAFONE,
                Instant.parse("2026-10-24T10:15:30Z"));
        ReflectionTestUtils.setField(request, "status", status);
        return request;
    }
}
