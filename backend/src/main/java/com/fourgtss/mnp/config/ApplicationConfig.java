package com.fourgtss.mnp.config;

import java.time.Clock;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.IdGenerator;

@Configuration
public class ApplicationConfig {

    /** Injected wherever "now" matters, so tests can control time. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** UUID v7: time-ordered, so inserts stay at the end of the primary-key index. */
    @Bean
    public IdGenerator idGenerator() {
        TimeBasedEpochGenerator uuidV7 = Generators.timeBasedEpochGenerator();
        return uuidV7::generate;
    }
}
