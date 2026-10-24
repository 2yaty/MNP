package com.fourgtss.mnp.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.repository.OperatorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(OrganizationHeaderInterceptorTest.WhoAmIController.class)
@Import({OrganizationHeaderInterceptor.class, OrganizationHeaderInterceptorTest.WhoAmIController.class})
class OrganizationHeaderInterceptorTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private OperatorRepository operatorRepository;

    @BeforeEach
    void knowsVodafoneOnly() {
        Operator vodafone = mock(Operator.class);
        given(vodafone.getCode()).willReturn("vodafone");
        given(operatorRepository.findByCode("vodafone")).willReturn(Optional.of(vodafone));
        given(operatorRepository.findByCode("Vodafone")).willReturn(Optional.empty());
        given(operatorRepository.findByCode("we")).willReturn(Optional.empty());
    }

    @Test
    @DisplayName("US-AUTH-1-AC1 a known organization header identifies the calling operator")
    void identifiesOperatorFromHeader() {
        assertThat(mvc.get().uri("/api/whoami").header("organization", "vodafone"))
                .hasStatusOk()
                .hasBodyTextEqualTo("vodafone");
    }

    @Test
    @DisplayName("US-AUTH-1-AC2 a missing organization header is rejected with 401")
    void rejectsMissingHeader() {
        assertThat(mvc.get().uri("/api/whoami"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson().extractingPath("$.detail").isEqualTo("Missing 'organization' header");
    }

    @Test
    @DisplayName("US-AUTH-1-AC2 a blank organization header is rejected with 401")
    void rejectsBlankHeader() {
        assertThat(mvc.get().uri("/api/whoami").header("organization", " "))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("US-AUTH-1-AC3 an unknown organization is rejected with 401")
    void rejectsUnknownOrganization() {
        assertThat(mvc.get().uri("/api/whoami").header("organization", "we"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson().extractingPath("$.detail").isEqualTo("Unknown organization 'we'");
    }

    @Test
    @DisplayName("US-AUTH-1-AC4 the organization value must match exactly")
    void rejectsWrongCase() {
        assertThat(mvc.get().uri("/api/whoami").header("organization", "Vodafone"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @RestController
    static class WhoAmIController {

        @GetMapping("/api/whoami")
        String whoAmI(@AuthenticatedOperator Operator operator) {
            return operator.getCode();
        }
    }
}
