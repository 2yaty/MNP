package com.fourgtss.mnp.service;

import static com.fourgtss.mnp.fixture.TestOperators.ETISALAT;
import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.fourgtss.mnp.common.BusinessRuleViolationException;
import com.fourgtss.mnp.common.ConflictException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
import com.fourgtss.mnp.repository.PortingRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class PortingRequestServiceTest {

    private static final String VODAFONE_NUMBER = "01012345678";
    private static final UUID REQUEST_ID = UUID.fromString("01a16f75-98d6-7022-be1d-787b044382b4");
    private static final Instant NOW = Instant.parse("2026-10-24T10:15:30.123456Z");

    @Mock
    private PortingRequestRepository portingRequestRepository;

    @Mock
    private PhoneNumberService phoneNumberService;

    private PortingRequestService service;

    @BeforeEach
    void createService() {
        service = new PortingRequestService(portingRequestRepository, phoneNumberService, () -> REQUEST_ID,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("US-R1-AC1 a valid request is PENDING, with the range owner as donor")
    void submitsPendingRequestToRangeOwner() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, VODAFONE);
        given(portingRequestRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortingRequest request = service.submit(ORANGE, VODAFONE_NUMBER);

        assertThat(request.getId()).isEqualTo(REQUEST_ID);
        assertThat(request.getPhoneNumber()).isEqualTo(VODAFONE_NUMBER);
        assertThat(request.getRecipient()).isEqualTo(ORANGE);
        assertThat(request.getDonor()).isEqualTo(VODAFONE);
        assertThat(request.getStatus()).isEqualTo(PortingStatus.PENDING);
        assertThat(request.getCreatedAt()).isEqualTo(Instant.parse("2026-10-24T10:15:30.123Z"));
        assertThat(request.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("US-R4-AC1 for a ported number, the donor is the current holder")
    void donorIsCurrentHolderOfPortedNumber() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, ORANGE);
        given(portingRequestRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.submit(ETISALAT, VODAFONE_NUMBER).getDonor()).isEqualTo(ORANGE);
    }

    @Test
    @DisplayName("US-R4-AC2 a number can be ported back to its range owner")
    void numberCanBePortedBackHome() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, ORANGE);
        given(portingRequestRepository.saveAndFlush(any())).willAnswer(invocation -> invocation.getArgument(0));

        PortingRequest request = service.submit(VODAFONE, VODAFONE_NUMBER);

        assertThat(request.getRecipient()).isEqualTo(VODAFONE);
        assertThat(request.getDonor()).isEqualTo(ORANGE);
    }

    @Test
    @DisplayName("US-R3-AC5 the recipient can't request a number it already holds")
    void rejectsNumberAlreadyHeldByRecipient() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, VODAFONE);

        assertThatThrownBy(() -> service.submit(VODAFONE, VODAFONE_NUMBER))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Vodafone already holds phone number 01012345678");
        verify(portingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("US-R4-AC3 the current holder of a ported number can't request it")
    void rejectsPortedNumberRequestedByCurrentHolder() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, ORANGE);

        assertThatThrownBy(() -> service.submit(ORANGE, VODAFONE_NUMBER))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("US-R3-AC6 a number outside every range is rejected")
    void rejectsNumberOutsideEveryRange() {
        given(phoneNumberService.findOwnership("01512345678")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(ORANGE, "01512345678"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Phone number 01512345678 is outside every operator's number range");
    }

    @Test
    @DisplayName("US-R3-AC1 a number with a pending request is rejected")
    void rejectsNumberWithPendingRequest() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, VODAFONE);
        given(portingRequestRepository.existsByPhoneNumberAndStatus(VODAFONE_NUMBER, PortingStatus.PENDING))
                .willReturn(true);

        assertThatThrownBy(() -> service.submit(ORANGE, VODAFONE_NUMBER))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Phone number 01012345678 already has a pending porting request");
        verify(portingRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("US-R3-AC4 losing an insert race on the pending-number key is reported as a conflict")
    void translatesPendingNumberKeyViolationToConflict() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, VODAFONE);
        given(portingRequestRepository.saveAndFlush(any())).willThrow(integrityViolation(
                "Duplicate entry '01012345678' for key 'porting_requests.uk_porting_requests_pending_phone_number'"));

        assertThatThrownBy(() -> service.submit(ORANGE, VODAFONE_NUMBER)).isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("Other integrity violations are not hidden as conflicts")
    void rethrowsOtherIntegrityViolations() {
        givenOwnership(VODAFONE_NUMBER, VODAFONE, VODAFONE);
        given(portingRequestRepository.saveAndFlush(any())).willThrow(integrityViolation(
                "Check constraint 'ck_porting_requests_phone_number' is violated."));

        assertThatThrownBy(() -> service.submit(ORANGE, VODAFONE_NUMBER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void givenOwnership(String phoneNumber, Operator rangeOwner, Operator currentHolder) {
        given(phoneNumberService.findOwnership(phoneNumber))
                .willReturn(Optional.of(new NumberOwnership(rangeOwner, currentHolder)));
    }

    private static DataIntegrityViolationException integrityViolation(String databaseMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(databaseMessage));
    }
}
