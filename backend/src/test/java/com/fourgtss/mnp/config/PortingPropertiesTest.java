package com.fourgtss.mnp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class PortingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnly.class);

    @Test
    @DisplayName("US-S1-AC5 the timeout and check interval are read from configuration")
    void bindsDurations() {
        contextRunner
                .withPropertyValues("mnp.porting.timeout=2m", "mnp.porting.expiry-check-interval=10s")
                .run(context -> assertThat(context.getBean(PortingProperties.class))
                        .isEqualTo(new PortingProperties(Duration.ofMinutes(2), Duration.ofSeconds(10))));
    }

    @Test
    @DisplayName("A zero timeout stops the app at startup")
    void rejectsZeroTimeout() {
        contextRunner
                .withPropertyValues("mnp.porting.timeout=0s", "mnp.porting.expiry-check-interval=10s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("A missing check interval stops the app at startup")
    void rejectsMissingInterval() {
        contextRunner
                .withPropertyValues("mnp.porting.timeout=2m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PortingProperties.class)
    static class PropertiesOnly {
    }
}
