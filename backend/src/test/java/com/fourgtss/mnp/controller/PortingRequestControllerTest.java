package com.fourgtss.mnp.controller;

import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fourgtss.mnp.auth.OrganizationHeaderInterceptor;
import com.fourgtss.mnp.common.BusinessRuleViolationException;
import com.fourgtss.mnp.common.ConflictException;
import com.fourgtss.mnp.common.ForbiddenException;
import com.fourgtss.mnp.common.NotFoundException;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
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
import org.springframework.test.util.ReflectionTestUtils;
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
    void knowsOrangeAndVodafone() {
        given(operatorRepository.findByCode("orange")).willReturn(Optional.of(ORANGE));
        given(operatorRepository.findByCode("vodafone")).willReturn(Optional.of(VODAFONE));
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

    @Test
    @DisplayName("US-A2-AC1 listing returns the requests the service says the caller may see")
    void listReturnsVisibleRequests() {
        PortingRequest request = PortingRequest.submit(REQUEST_ID, "01012345678", ORANGE, VODAFONE,
                Instant.parse("2026-10-24T10:15:30.123Z"));
        given(portingRequestService.findVisibleTo(ORANGE)).willReturn(List.of(request));

        assertThat(mvc.get().uri("/api/porting-requests").header("organization", "orange"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        [{"id": "%s", "recipient": "orange", "donor": "vodafone", "status": "PENDING"}]"""
                        .formatted(REQUEST_ID));
    }

    @Test
    @DisplayName("US-R5-AC2 getting a request by id returns it")
    void getReturnsRequest() {
        PortingRequest request = PortingRequest.submit(REQUEST_ID, "01012345678", ORANGE, VODAFONE,
                Instant.parse("2026-10-24T10:15:30.123Z"));
        given(portingRequestService.getVisible(ORANGE, REQUEST_ID)).willReturn(request);

        assertThat(mvc.get().uri("/api/porting-requests/{id}", REQUEST_ID).header("organization", "orange"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.id").isEqualTo(REQUEST_ID.toString());
    }

    @Test
    @DisplayName("US-T1-AC3 a request the caller can't see gives 404")
    void getMapsNotFoundTo404() {
        given(portingRequestService.getVisible(ORANGE, REQUEST_ID))
                .willThrow(new NotFoundException("Porting request %s not found".formatted(REQUEST_ID)));

        assertThat(mvc.get().uri("/api/porting-requests/{id}", REQUEST_ID).header("organization", "orange"))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("Getting a request whose id isn't a UUID gives 400")
    void getRejectsNonUuidId() {
        assertThat(mvc.get().uri("/api/porting-requests/42").header("organization", "orange"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(portingRequestService);
    }

    @Test
    @DisplayName("US-D1-AC1 accepting returns 200 with the resolved request")
    void acceptReturnsResolvedRequest() {
        PortingRequest request = PortingRequest.submit(REQUEST_ID, "01012345678", ORANGE, VODAFONE,
                Instant.parse("2026-10-24T10:15:30.123Z"));
        ReflectionTestUtils.setField(request, "status", PortingStatus.ACCEPTED);
        ReflectionTestUtils.setField(request, "resolvedAt", Instant.parse("2026-10-24T10:16:00Z"));
        given(portingRequestService.accept(VODAFONE, REQUEST_ID)).willReturn(request);

        assertThat(answer("vodafone", REQUEST_ID + "/accept"))
                .hasStatusOk()
                .bodyJson().isLenientlyEqualTo("""
                        {"id": "%s", "status": "ACCEPTED", "resolvedAt": "2026-10-24T10:16:00Z"}""".formatted(REQUEST_ID));
    }

    @Test
    @DisplayName("US-D4-AC5 a request id that isn't a UUID is rejected with 400")
    void rejectsNonUuidId() {
        assertThat(answer("vodafone", "42/accept")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(answer("vodafone", "42/reject")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(portingRequestService);
    }

    @Test
    @DisplayName("US-D3-AC1 a caller that isn't the donor gets 403")
    void mapsForbiddenTo403() {
        given(portingRequestService.reject(ORANGE, REQUEST_ID))
                .willThrow(new ForbiddenException("Only the donor can accept or reject porting request " + REQUEST_ID));

        assertThat(answer("orange", REQUEST_ID + "/reject")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("US-D4-AC2 a request the caller can't find gives 404")
    void mapsNotFoundTo404() {
        given(portingRequestService.accept(VODAFONE, REQUEST_ID))
                .willThrow(new NotFoundException("Porting request %s not found".formatted(REQUEST_ID)));

        assertThat(answer("vodafone", REQUEST_ID + "/accept"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }

    private MvcTestResult answer(String organization, String path) {
        return mvc.post().uri("/api/porting-requests/" + path).header("organization", organization).exchange();
    }

    private MvcTestResult submit(String body) {
        return mvc.post().uri("/api/porting-requests")
                .header("organization", "orange")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }
}
