package com.fourgtss.mnp.controller;

import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fourgtss.mnp.auth.OrganizationHeaderInterceptor;
import com.fourgtss.mnp.common.BusinessRuleViolationException;
import com.fourgtss.mnp.common.ConflictException;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.repository.OperatorRepository;
import com.fourgtss.mnp.service.PortingRequestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PortingRequestController.class)
@Import(OrganizationHeaderInterceptor.class)
class PortingRequestControllerTest {

    private static final UUID REQUEST_ID = UUID.fromString("01a16f75-98d6-7022-be1d-787b044382b4");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PortingRequestService portingRequestService;

    @MockitoBean
    private OperatorRepository operatorRepository;

    @BeforeEach
    void orangeIsCalling() {
        given(operatorRepository.findByCode("orange")).willReturn(Optional.of(ORANGE));
    }

    @Test
    @DisplayName("US-R1-AC1 a valid request returns 201 with the new PENDING request and its location")
    void submitReturnsCreatedRequest() {
        PortingRequest request = PortingRequest.submit(REQUEST_ID, "01012345678", ORANGE, VODAFONE,
                Instant.parse("2026-10-24T10:15:30.123Z"));
        given(portingRequestService.submit(ORANGE, "01012345678")).willReturn(request);

        MvcTestResult result = submit("""
                {"phoneNumber": "01012345678"}""");

        assertThat(result).hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/porting-requests/" + REQUEST_ID);
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {
                  "id": "01a16f75-98d6-7022-be1d-787b044382b4",
                  "phoneNumber": "01012345678",
                  "recipient": "orange",
                  "donor": "vodafone",
                  "status": "PENDING",
                  "createdAt": "2026-10-24T10:15:30.123Z",
                  "resolvedAt": null
                }""");
    }

    @Test
    @DisplayName("US-R1-AC3 clients can't set the donor or status")
    void rejectsUnknownFields() {
        assertThat(submit("""
                {"phoneNumber": "01012345678", "donor": "etisalat"}""")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(submit("""
                {"phoneNumber": "01012345678", "status": "ACCEPTED"}""")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(portingRequestService);
    }

    @Test
    @DisplayName("US-R2-AC1 a missing phone number is rejected and the error names the field")
    void rejectsMissingPhoneNumber() {
        assertThat(submit("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.errors.phoneNumber").isEqualTo("is required");
        verifyNoInteractions(portingRequestService);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"0101234567", "010123456789", "0101234567a", "+201012345678", "21012345678", ""})
    @DisplayName("US-R2-AC2/AC3 a badly formatted phone number is rejected")
    void rejectsBadlyFormattedPhoneNumber(String phoneNumber) {
        assertThat(submit("""
                {"phoneNumber": "%s"}""".formatted(phoneNumber)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.phoneNumber")
                .isEqualTo("must be an 11-digit mobile number starting with 01");
        verifyNoInteractions(portingRequestService);
    }

    @Test
    @DisplayName("US-R2-AC4 a list of phone numbers is rejected")
    void rejectsListOfPhoneNumbers() {
        assertThat(submit("""
                {"phoneNumber": ["01012345678", "01112345678"]}""")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(portingRequestService);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "{\"phoneNumber\":", "not json"})
    @DisplayName("US-R2-AC5 an empty body or malformed JSON is rejected")
    void rejectsEmptyOrMalformedBody(String body) {
        assertThat(submit(body)).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(portingRequestService);
    }

    @Test
    @DisplayName("US-R3-AC1 a pending-request conflict returns 409")
    void mapsConflictTo409() {
        given(portingRequestService.submit(any(), any()))
                .willThrow(new ConflictException("Phone number 01012345678 already has a pending porting request"));

        assertThat(submit("""
                {"phoneNumber": "01012345678"}"""))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson().extractingPath("$.detail")
                .isEqualTo("Phone number 01012345678 already has a pending porting request");
    }

    @Test
    @DisplayName("US-R3-AC5/AC6 a business rule violation returns 422")
    void mapsBusinessRuleViolationTo422() {
        given(portingRequestService.submit(any(), any()))
                .willThrow(new BusinessRuleViolationException("Orange already holds phone number 01212345678"));

        assertThat(submit("""
                {"phoneNumber": "01212345678"}"""))
                .hasStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .bodyJson().extractingPath("$.detail").isEqualTo("Orange already holds phone number 01212345678");
    }

    private MvcTestResult submit(String body) {
        return mvc.post().uri("/api/porting-requests")
                .header("organization", "orange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
