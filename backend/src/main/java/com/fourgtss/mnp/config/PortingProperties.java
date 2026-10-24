package com.fourgtss.mnp.config;

import java.time.Duration;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Porting settings under {@code mnp.porting}. Checked at startup, so a bad value stops the app.
 *
 * @param timeout             how long a request may stay pending before it's canceled
 * @param expiryCheckInterval how often to look for timed-out requests
 */
@ConfigurationProperties("mnp.porting")
@Validated
public record PortingProperties(
        @NotNull @DurationMin(millis = 1) Duration timeout,
        @NotNull @DurationMin(millis = 1) Duration expiryCheckInterval) {
}
