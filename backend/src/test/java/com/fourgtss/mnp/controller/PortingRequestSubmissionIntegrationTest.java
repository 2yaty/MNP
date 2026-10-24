package com.fourgtss.mnp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Submitting porting requests end to end, against MySQL with the real schema and constraints. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortingRequestSubmissionIntegrationTest {

    private static final String VODAFONE_NUMBER = "01012345678";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearPortingData() {
        jdbcTemplate.update("DELETE FROM porting_requests");
        jdbcTemplate.update("DELETE FROM ported_numbers");
    }

    @Test
    @DisplayName("US-R1-AC1 a submitted request is stored as PENDING with the derived donor")
    void storesSubmittedRequest() {
        assertThat(submit("orange", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.donor").isEqualTo("vodafone");

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT r.code AS recipient, d.code AS donor, p.status, p.resolved_at
                FROM porting_requests p
                JOIN operators r ON r.id = p.recipient_operator_id
                JOIN operators d ON d.id = p.donor_operator_id
                WHERE p.phone_number = ?""", VODAFONE_NUMBER);
        assertThat(row).containsEntry("recipient", "orange")
                .containsEntry("donor", "vodafone")
                .containsEntry("status", "PENDING")
                .containsEntry("resolved_at", null);
    }

    @Test
    @DisplayName("US-R3-AC2 a second request for a pending number is rejected, even from another recipient")
    void rejectsSecondPendingRequest() {
        assertThat(submit("orange", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED);

        assertThat(submit("etisalat", VODAFONE_NUMBER)).hasStatus(HttpStatus.CONFLICT);
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("US-R3-AC3 a new request is allowed once the previous one is resolved")
    void allowsNewRequestAfterPreviousIsResolved() {
        assertThat(submit("orange", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED);
        jdbcTemplate.update("UPDATE porting_requests SET status = 'REJECTED', resolved_at = UTC_TIMESTAMP(3)");

        assertThat(submit("etisalat", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED);
        assertThat(countRequests()).isEqualTo(2);
    }

    @Test
    @DisplayName("US-R3-AC4 concurrent requests for the same number: exactly one wins")
    void onlyOneOfConcurrentRequestsWins() throws Exception {
        int requests = 10;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> submitWhenReleased = () -> {
            start.await();
            return submit("orange", VODAFONE_NUMBER).getResponse().getStatus();
        };

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            List<Future<Integer>> futures = IntStream.range(0, requests)
                    .mapToObj(i -> executor.submit(submitWhenReleased))
                    .toList();
            start.countDown();
            statuses = futures.stream().map(PortingRequestSubmissionIntegrationTest::await).toList();
        }

        assertThat(statuses).containsOnlyOnce(HttpStatus.CREATED.value());
        assertThat(statuses).filteredOn(status -> status != HttpStatus.CREATED.value())
                .containsOnly(HttpStatus.CONFLICT.value());
        assertThat(countRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("US-R4-AC1 for a ported number, the donor is the current holder")
    void donorOfPortedNumberIsCurrentHolder() {
        jdbcTemplate.update("INSERT INTO ported_numbers (phone_number, current_operator_id) "
                + "SELECT ?, id FROM operators WHERE code = 'orange'", VODAFONE_NUMBER);

        assertThat(submit("etisalat", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED)
                .bodyJson().extractingPath("$.donor").isEqualTo("orange");
    }

    @Test
    @DisplayName("US-R4-AC3 the current holder of a ported number can't request it")
    void currentHolderOfPortedNumberCannotRequestIt() {
        jdbcTemplate.update("INSERT INTO ported_numbers (phone_number, current_operator_id) "
                + "SELECT ?, id FROM operators WHERE code = 'orange'", VODAFONE_NUMBER);

        assertThat(submit("orange", VODAFONE_NUMBER)).hasStatus(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(countRequests()).isZero();
    }

    private MvcTestResult submit(String organization, String phoneNumber) {
        return mvc.post().uri("/api/porting-requests")
                .header("organization", organization)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phoneNumber": "%s"}""".formatted(phoneNumber))
                .exchange();
    }

    private int countRequests() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM porting_requests", Integer.class);
    }

    private static Integer await(Future<Integer> future) {
        try {
            return future.get();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
