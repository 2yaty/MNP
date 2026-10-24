package com.fourgtss.mnp.config;

import com.fourgtss.mnp.service.PortingRequestExpiryJob;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Schedules the expiry job from the bound {@link PortingProperties}, so the interval is read from one place
 * and is validated like the other settings.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PortingProperties.class)
public class SchedulingConfig implements SchedulingConfigurer {

    private final PortingRequestExpiryJob portingRequestExpiryJob;
    private final PortingProperties portingProperties;

    public SchedulingConfig(PortingRequestExpiryJob portingRequestExpiryJob, PortingProperties portingProperties) {
        this.portingRequestExpiryJob = portingRequestExpiryJob;
        this.portingProperties = portingProperties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(portingRequestExpiryJob::cancelExpiredRequests,
                portingProperties.expiryCheckInterval());
    }
}
