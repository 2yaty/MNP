package com.fourgtss.mnp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fourgtss.mnp.config.PortingProperties;
import com.fourgtss.mnp.repository.PortingRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortingRequestExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-24T10:15:30.123456Z");

    @Mock
    private PortingRequestRepository portingRequestRepository;

    @Test
    @DisplayName("US-S1-AC1/AC5 cancels requests pending since before now minus the configured timeout")
    void cancelsRequestsOlderThanTimeout() {
        PortingRequestExpiryJob job = new PortingRequestExpiryJob(portingRequestRepository,
                new PortingProperties(Duration.ofMinutes(2), Duration.ofSeconds(10)), Clock.fixed(NOW, ZoneOffset.UTC));
        given(portingRequestRepository.cancelPendingCreatedBefore(
                Instant.parse("2026-10-24T10:13:30.123Z"), Instant.parse("2026-10-24T10:15:30.123Z")))
                .willReturn(3);

        assertThat(job.cancelExpiredRequests()).isEqualTo(3);
    }
}
