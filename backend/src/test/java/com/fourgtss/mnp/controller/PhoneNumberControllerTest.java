package com.fourgtss.mnp.controller;

import static com.fourgtss.mnp.fixture.TestOperators.ETISALAT;
import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.Optional;

import com.fourgtss.mnp.auth.OrganizationHeaderInterceptor;
import com.fourgtss.mnp.common.NotFoundException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.repository.OperatorRepository;
import com.fourgtss.mnp.service.PhoneNumberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(PhoneNumberController.class)
@Import(OrganizationHeaderInterceptor.class)
class PhoneNumberControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PhoneNumberService phoneNumberService;

    @MockitoBean
    private OperatorRepository operatorRepository;

    @BeforeEach
    void etisalatIsCalling() {
        given(operatorRepository.findByCode("etisalat")).willReturn(Optional.of(ETISALAT));
    }

    @Test
    @DisplayName("US-A1-AC2 the status shows the range owner, the current holder and whether it's ported")
    void returnsStatus() {
        given(phoneNumberService.getOwnership("01012345678")).willReturn(new NumberOwnership(VODAFONE, ORANGE));

        assertThat(getStatus("01012345678"))
                .hasStatusOk()
                .bodyJson().isStrictlyEqualTo("""
                        {
                          "phoneNumber": "01012345678",
                          "rangeOwner": "vodafone",
                          "currentHolder": "orange",
                          "ported": true
                        }""");
    }

    @Test
    @DisplayName("US-A1-AC5 a badly formatted number gives 400 naming the parameter")
    void rejectsBadlyFormattedNumber() {
        assertThat(getStatus("0101"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.errors.phoneNumber")
                .isEqualTo("must be an 11-digit mobile number starting with 01");
        verifyNoInteractions(phoneNumberService);
    }

    @Test
    @DisplayName("US-A1-AC6 a number outside every range gives 404")
    void numberOutsideEveryRangeGives404() {
        given(phoneNumberService.getOwnership("01512345678")).willThrow(
                new NotFoundException("Phone number 01512345678 is outside every operator's number range"));

        assertThat(getStatus("01512345678")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("The lookup requires the organization header like every API endpoint")
    void requiresOrganizationHeader() {
        assertThat(mvc.get().uri("/api/phone-numbers/01012345678")).hasStatus(HttpStatus.UNAUTHORIZED);
    }

    private MvcTestResult getStatus(String phoneNumber) {
        return mvc.get().uri("/api/phone-numbers/{phoneNumber}", phoneNumber)
                .header("organization", "etisalat")
                .exchange();
    }
}
