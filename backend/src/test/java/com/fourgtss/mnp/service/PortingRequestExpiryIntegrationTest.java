package com.fourgtss.mnp.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.fourgtss.mnp.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The scheduled expiry job, running for real against MySQL. A 5-minute timeout (not the default 2) proves
 * the configured value is used, and a short check interval keeps the test fast.
 */
@SpringBootTest(properties = {"mnp.porting.timeout=5m", "mnp.porting.expiry-check-interval=200ms"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortingRequestExpiryIntegrationTest {

    private static final Duration WAIT_LIMIT = Duration.ofSeconds(10);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PortingRequestExpiryJob job;

    @BeforeEach
    void clearPortingData() {
        jdbcTemplate.update("DELETE FROM porting_requests");
        jdbcTemplate.update("DELETE FROM ported_numbers");
    }

    @Test
    @DisplayName("US-S1-AC1/AC2/AC3/AC5 only requests pending longer than the configured timeout are canceled")
    void cancelsOnlyTimedOutPendingRequests() {
        String expired = submitCreatedMinutesAgo("01000000001", 6);
        String young = submitCreatedMinutesAgo("01000000002", 3);   // past the 2m default, within the 5m setting
        String answered = submitCreatedMinutesAgo("01000000003", 6);
        jdbcTemplate.update("UPDATE porting_requests SET status = 'ACCEPTED', resolved_at = UTC_TIMESTAMP(3) "
                + "WHERE phone_number = ?", answered);

        waitUntil(() -> "CANCELED".equals(stored(expired).get("status")));

        assertThat(stored(expired).get("resolved_at")).isNotNull();
        assertThat(stored(young)).containsEntry("status", "PENDING").containsEntry("resolved_at", null);
        assertThat(stored(answered)).containsEntry("status", "ACCEPTED");
    }

    @Test
    @DisplayName("US-S1-AC4 a canceled request leaves the holder alone and frees the number")
    void canceledRequestFreesTheNumber() {
        String phoneNumber = submitCreatedMinutesAgo("01000000004", 6);
        waitUntil(() -> "CANCELED".equals(stored(phoneNumber).get("status")));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ported_numbers", Integer.class)).isZero();
        assertThat(mvc.post().uri("/api/porting-requests")
                .header("organization", "etisalat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phoneNumber": "%s"}""".formatted(phoneNumber)))
                .hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("US-S1-AC6 running again after a cancel changes nothing")
    void secondRunChangesNothing() {
        String phoneNumber = submitCreatedMinutesAgo("01000000005", 6);
        waitUntil(() -> "CANCELED".equals(stored(phoneNumber).get("status")));
        Object resolvedAt = stored(phoneNumber).get("resolved_at");

        assertThat(job.cancelExpiredRequests()).isZero();
        assertThat(stored(phoneNumber)).containsEntry("status", "CANCELED").containsEntry("resolved_at", resolvedAt);
    }

    /** Submits through the API, then moves {@code created_at} into the past. */
    private String submitCreatedMinutesAgo(String phoneNumber, int minutes) {
        assertThat(mvc.post().uri("/api/porting-requests")
                .header("organization", "orange")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phoneNumber": "%s"}""".formatted(phoneNumber)))
                .hasStatus(HttpStatus.CREATED);
        jdbcTemplate.update("UPDATE porting_requests SET created_at = UTC_TIMESTAMP(3) - INTERVAL ? MINUTE "
                + "WHERE phone_number = ?", minutes, phoneNumber);
        return phoneNumber;
    }

    private Map<String, Object> stored(String phoneNumber) {
        return jdbcTemplate.queryForMap(
                "SELECT status, resolved_at FROM porting_requests WHERE phone_number = ?", phoneNumber);
    }

    private static void waitUntil(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(WAIT_LIMIT);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Condition not met within " + WAIT_LIMIT);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting", exception);
            }
        }
    }
}
