package com.fourgtss.mnp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import com.fourgtss.mnp.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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

/** Donors accepting and rejecting porting requests, end to end against MySQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortingRequestResolutionIntegrationTest {

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
    @DisplayName("US-D1-AC1/AC2 accepting resolves the request and moves the number to the recipient")
    void acceptMovesNumberToRecipient() {
        UUID id = submit("orange", VODAFONE_NUMBER);

        assertThat(answer("vodafone", id, "accept")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");

        assertThat(storedRequest(id)).containsEntry("status", "ACCEPTED");
        assertThat(storedRequest(id).get("resolved_at")).isNotNull();
        assertThat(currentHolder(VODAFONE_NUMBER)).isEqualTo("orange");
    }

    @Test
    @DisplayName("US-D2-AC1/AC2/AC3 rejecting resolves the request, keeps the holder and frees the number")
    void rejectKeepsNumberWithDonor() {
        UUID id = submit("orange", VODAFONE_NUMBER);

        assertThat(answer("vodafone", id, "reject")).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("REJECTED");

        assertThat(storedRequest(id)).containsEntry("status", "REJECTED");
        assertThat(currentHolder(VODAFONE_NUMBER)).isNull();
        assertThat(submitResult("etisalat", VODAFONE_NUMBER)).hasStatus(HttpStatus.CREATED);
    }

    @Test
    @DisplayName("Porting a ported number again updates its current holder")
    void portingAgainUpdatesCurrentHolder() {
        assertThat(answer("vodafone", submit("orange", VODAFONE_NUMBER), "accept")).hasStatusOk();

        UUID second = submit("etisalat", VODAFONE_NUMBER);
        assertThat(storedRequest(second)).containsEntry("donor", "orange");
        assertThat(answer("orange", second, "accept")).hasStatusOk();

        assertThat(currentHolder(VODAFONE_NUMBER)).isEqualTo("etisalat");
    }

    @Test
    @DisplayName("US-D3-AC1 the recipient gets 403 and the request stays pending")
    void recipientCannotAnswer() {
        UUID id = submit("orange", VODAFONE_NUMBER);

        assertThat(answer("orange", id, "accept")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(storedRequest(id)).containsEntry("status", "PENDING");
    }

    @Test
    @DisplayName("US-D3-AC2 a third party gets 404 and the request stays pending")
    void thirdPartyCannotAnswer() {
        UUID id = submit("orange", VODAFONE_NUMBER);

        assertThat(answer("etisalat", id, "reject")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(storedRequest(id)).containsEntry("status", "PENDING");
    }

    @Test
    @DisplayName("US-D4-AC1 a request can only be answered once")
    void answeredRequestCannotBeAnsweredAgain() {
        UUID id = submit("orange", VODAFONE_NUMBER);
        assertThat(answer("vodafone", id, "accept")).hasStatusOk();

        assertThat(answer("vodafone", id, "reject")).hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Porting request %s is already ACCEPTED".formatted(id));
        assertThat(storedRequest(id)).containsEntry("status", "ACCEPTED");
    }

    @Test
    @DisplayName("US-D4-AC2 answering a request that doesn't exist gives 404")
    void missingRequestGivesNotFound() {
        assertThat(answer("vodafone", UUID.randomUUID(), "accept")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("US-D4-AC3 a canceled request can't be accepted and the number doesn't move")
    void canceledRequestCannotBeAccepted() {
        UUID id = submit("orange", VODAFONE_NUMBER);
        jdbcTemplate.update("UPDATE porting_requests SET status = 'CANCELED', resolved_at = UTC_TIMESTAMP(3)");

        assertThat(answer("vodafone", id, "accept")).hasStatus(HttpStatus.CONFLICT);
        assertThat(storedRequest(id)).containsEntry("status", "CANCELED");
        assertThat(currentHolder(VODAFONE_NUMBER)).isNull();
    }

    @RepeatedTest(5)
    @DisplayName("US-D4-AC4 accept racing a cancel: one wins, and the number moves only if accept won")
    void acceptRacingCancelLeavesConsistentState() throws Exception {
        UUID id = submit("orange", VODAFONE_NUMBER);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> accept = executor.submit(() -> {
                start.await();
                return answer("vodafone", id, "accept").getResponse().getStatus();
            });
            // Stands in for the timeout job, which runs the same kind of guarded update.
            Future<Integer> cancel = executor.submit(() -> {
                start.await();
                return jdbcTemplate.update("UPDATE porting_requests SET status = 'CANCELED', "
                        + "resolved_at = UTC_TIMESTAMP(3) WHERE status = 'PENDING'");
            });
            start.countDown();

            boolean acceptWon = accept.get() == HttpStatus.OK.value();
            assertThat(cancel.get()).isEqualTo(acceptWon ? 0 : 1);
            assertThat(storedRequest(id)).containsEntry("status", acceptWon ? "ACCEPTED" : "CANCELED");
            assertThat(currentHolder(VODAFONE_NUMBER)).isEqualTo(acceptWon ? "orange" : null);
        }
    }

    @Test
    @DisplayName("Accept racing reject: exactly one answer wins")
    void acceptRacingRejectHasOneWinner() throws Exception {
        UUID id = submit("orange", VODAFONE_NUMBER);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> statuses;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> answers = Stream.of("accept", "reject")
                    .map(action -> executor.submit(() -> {
                        start.await();
                        return answer("vodafone", id, action).getResponse().getStatus();
                    }))
                    .toList();
            start.countDown();
            statuses = List.of(answers.get(0).get(), answers.get(1).get());
        }

        assertThat(statuses).containsExactlyInAnyOrder(HttpStatus.OK.value(), HttpStatus.CONFLICT.value());
    }

    private UUID submit(String recipient, String phoneNumber) {
        MvcTestResult result = submitResult(recipient, phoneNumber);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        return UUID.fromString(result.getResponse().getHeader("Location").replaceAll(".*/", ""));
    }

    private MvcTestResult submitResult(String recipient, String phoneNumber) {
        return mvc.post().uri("/api/porting-requests")
                .header("organization", recipient)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"phoneNumber": "%s"}""".formatted(phoneNumber))
                .exchange();
    }

    private MvcTestResult answer(String organization, UUID id, String action) {
        return mvc.post().uri("/api/porting-requests/{id}/{action}", id, action)
                .header("organization", organization)
                .exchange();
    }

    private Map<String, Object> storedRequest(UUID id) {
        return jdbcTemplate.queryForMap("""
                SELECT p.status, p.resolved_at, d.code AS donor
                FROM porting_requests p JOIN operators d ON d.id = p.donor_operator_id
                WHERE p.id = UUID_TO_BIN(?)""", id.toString());
    }

    private String currentHolder(String phoneNumber) {
        return jdbcTemplate.query("""
                SELECT o.code FROM ported_numbers n JOIN operators o ON o.id = n.current_operator_id
                WHERE n.phone_number = ?""", (row, index) -> row.getString(1), phoneNumber)
                .stream().findFirst().orElse(null);
    }
}
