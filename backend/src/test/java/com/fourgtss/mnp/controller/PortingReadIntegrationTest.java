package com.fourgtss.mnp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import com.fourgtss.mnp.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
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

/**
 * Listing, fetching and number lookups against MySQL. Each test starts from the same data: Orange requests
 * four Vodafone numbers, one left in each status, and Etisalat requests one Orange number.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PortingReadIntegrationTest {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID pending;
    private UUID accepted;
    private UUID rejected;
    private UUID canceled;
    private UUID etisalatFromOrange;

    @BeforeEach
    void createRequestsInEveryStatus() {
        jdbcTemplate.update("DELETE FROM porting_requests");
        jdbcTemplate.update("DELETE FROM ported_numbers");

        pending = submit("orange", "01000000001");
        accepted = submit("orange", "01000000002");
        answer("vodafone", accepted, "accept");
        rejected = submit("orange", "01000000003");
        answer("vodafone", rejected, "reject");
        canceled = submit("orange", "01000000004");
        jdbcTemplate.update("UPDATE porting_requests SET status = 'CANCELED', resolved_at = UTC_TIMESTAMP(3) "
                + "WHERE id = UUID_TO_BIN(?)", canceled.toString());
        etisalatFromOrange = submit("etisalat", "01200000005");
    }

    @Test
    @DisplayName("US-R5-AC1 the recipient sees all its requests, in every status")
    void recipientSeesAllItsRequests() {
        assertThat(listedIds("orange")).containsExactlyInAnyOrder(pending, accepted, rejected, canceled,
                etisalatFromOrange);
    }

    @Test
    @DisplayName("US-D5-AC1 the donor sees all requests addressed to it, in every status")
    void donorSeesAllRequestsAddressedToIt() {
        assertThat(listedIds("vodafone")).containsExactlyInAnyOrder(pending, accepted, rejected, canceled);
    }

    @Test
    @DisplayName("US-T1-AC1 a third party sees only the accepted requests of others")
    void thirdPartySeesOnlyAcceptedRequests() {
        assertThat(listedIds("etisalat")).containsExactlyInAnyOrder(accepted, etisalatFromOrange);
    }

    @Test
    @DisplayName("US-A2-AC2 each request appears once, newest first")
    void listIsNewestFirstWithoutDuplicates() {
        assertThat(listedIds("orange")).containsExactly(etisalatFromOrange, canceled, rejected, accepted, pending);
    }

    @Test
    @DisplayName("US-R5-AC2 the recipient can fetch its request in any status")
    void recipientCanFetchAnyOfItsRequests() {
        for (UUID id : List.of(pending, accepted, rejected, canceled)) {
            assertThat(get("orange", id)).hasStatusOk();
        }
    }

    @Test
    @DisplayName("US-T1-AC2 a third party can fetch an accepted request")
    void thirdPartyCanFetchAcceptedRequest() {
        assertThat(get("etisalat", accepted)).hasStatusOk()
                .bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");
    }

    @Test
    @DisplayName("US-T1-AC3 a third party gets 404 for pending, rejected and canceled requests")
    void thirdPartyCannotFetchOtherRequests() {
        for (UUID id : List.of(pending, rejected, canceled)) {
            assertThat(get("etisalat", id)).hasStatus(HttpStatus.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("The Location returned on submit points to the new request")
    void locationPointsToNewRequest() {
        MvcTestResult created = submitResult("orange", "01000000009");

        assertThat(mvc.get().uri(created.getResponse().getHeader("Location")).header("organization", "orange"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.phoneNumber").isEqualTo("01000000009");
    }

    @Test
    @DisplayName("US-A1-AC1 a number that was never ported is held by its range owner")
    void neverPortedNumberStatus() {
        assertThat(status("01100000009")).bodyJson().isLenientlyEqualTo("""
                {"rangeOwner": "etisalat", "currentHolder": "etisalat", "ported": false}""");
    }

    @Test
    @DisplayName("US-A1-AC2 an accepted port moves the holder")
    void portedNumberStatus() {
        assertThat(status("01000000002")).bodyJson().isLenientlyEqualTo("""
                {"rangeOwner": "vodafone", "currentHolder": "orange", "ported": true}""");
    }

    @Test
    @DisplayName("US-A1-AC3 a number ported back to its range owner counts as not ported")
    void numberPortedBackHomeStatus() {
        UUID backHome = submit("vodafone", "01000000002");
        answer("orange", backHome, "accept");

        assertThat(status("01000000002")).bodyJson().isLenientlyEqualTo("""
                {"rangeOwner": "vodafone", "currentHolder": "vodafone", "ported": false}""");
    }

    @Test
    @DisplayName("US-A1-AC4 pending and rejected requests don't change the holder")
    void unresolvedRequestsDoNotMoveTheNumber() {
        for (String phoneNumber : List.of("01000000001", "01000000003")) {
            assertThat(status(phoneNumber)).bodyJson().isLenientlyEqualTo("""
                    {"currentHolder": "vodafone", "ported": false}""");
        }
    }

    @Test
    @DisplayName("US-A1-AC7 every operator gets the same answer")
    void everyOperatorGetsTheSameStatus() {
        String expected = """
                {"phoneNumber": "01000000002", "rangeOwner": "vodafone", "currentHolder": "orange", "ported": true}""";
        for (String organization : List.of("vodafone", "orange", "etisalat")) {
            assertThat(mvc.get().uri("/api/phone-numbers/01000000002").header("organization", organization))
                    .hasStatusOk()
                    .bodyJson().isStrictlyEqualTo(expected);
        }
    }

    private List<UUID> listedIds(String organization) {
        MvcTestResult result = mvc.get().uri("/api/porting-requests").header("organization", organization).exchange();
        assertThat(result).hasStatusOk();
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        List<String> ids = JsonPath.read(body, "$[*].id");
        return ids.stream().map(UUID::fromString).toList();
    }

    private MvcTestResult get(String organization, UUID id) {
        return mvc.get().uri("/api/porting-requests/{id}", id).header("organization", organization).exchange();
    }

    private MvcTestResult status(String phoneNumber) {
        MvcTestResult result = mvc.get().uri("/api/phone-numbers/{phoneNumber}", phoneNumber)
                .header("organization", "etisalat")
                .exchange();
        assertThat(result).hasStatusOk();
        return result;
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

    private void answer(String organization, UUID id, String action) {
        assertThat(mvc.post().uri("/api/porting-requests/{id}/{action}", id, action)
                .header("organization", organization))
                .hasStatusOk();
    }
}
