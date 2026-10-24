package com.fourgtss.mnp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fourgtss.mnp.config.PortingProperties;
import com.fourgtss.mnp.repository.PortingRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancels pending requests that the donor didn't answer within {@code mnp.porting.timeout}. Scheduled by
 * {@link com.fourgtss.mnp.config.SchedulingConfig}. Uses the same guarded update as accept and reject, so a donor
 * answering at the same moment never leaves a mixed state, and running it on several app instances is harmless.
 */
@Component
public class PortingRequestExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(PortingRequestExpiryJob.class);

    private final PortingRequestRepository portingRequestRepository;
    private final PortingProperties portingProperties;
    private final Clock clock;

    public PortingRequestExpiryJob(PortingRequestRepository portingRequestRepository,
                                   PortingProperties portingProperties, Clock clock) {
        this.portingRequestRepository = portingRequestRepository;
        this.portingProperties = portingProperties;
        this.clock = clock;
    }

    /** @return how many requests were canceled */
    @Transactional
    public int cancelExpiredRequests() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        int canceled = portingRequestRepository.cancelPendingCreatedBefore(
                now.minus(portingProperties.timeout()), now);
        if (canceled > 0) {
            log.info("Canceled {} porting request(s) pending longer than {}", canceled, portingProperties.timeout());
        }
        return canceled;
    }
}
