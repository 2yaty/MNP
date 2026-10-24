package com.fourgtss.mnp.service;

import static com.fourgtss.mnp.fixture.TestOperators.ORANGE;
import static com.fourgtss.mnp.fixture.TestOperators.VODAFONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import com.fourgtss.mnp.common.NotFoundException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortedNumber;
import com.fourgtss.mnp.repository.OperatorRepository;
import com.fourgtss.mnp.repository.PortedNumberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class PhoneNumberServiceTest {

    private static final String VODAFONE_NUMBER = "01012345678";

    @Mock
    private OperatorRepository operatorRepository;

    @Mock
    private PortedNumberRepository portedNumberRepository;

    @InjectMocks
    private PhoneNumberService service;

    @Test
    @DisplayName("US-A1-AC1 a number that was never ported is held by its range owner")
    void neverPortedNumberIsHeldByRangeOwner() {
        given(operatorRepository.findRangeOwner(VODAFONE_NUMBER)).willReturn(Optional.of(VODAFONE));
        given(portedNumberRepository.findByPhoneNumber(VODAFONE_NUMBER)).willReturn(Optional.empty());

        assertThat(service.getOwnership(VODAFONE_NUMBER)).isEqualTo(new NumberOwnership(VODAFONE, VODAFONE));
    }

    @Test
    @DisplayName("US-A1-AC2 a ported number is held by the operator recorded in ported_numbers")
    void portedNumberIsHeldByRecordedOperator() {
        given(operatorRepository.findRangeOwner(VODAFONE_NUMBER)).willReturn(Optional.of(VODAFONE));
        given(portedNumberRepository.findByPhoneNumber(VODAFONE_NUMBER))
                .willReturn(Optional.of(portedNumber(VODAFONE_NUMBER, ORANGE)));

        assertThat(service.getOwnership(VODAFONE_NUMBER)).isEqualTo(new NumberOwnership(VODAFONE, ORANGE));
    }

    @Test
    @DisplayName("US-A1-AC6 a number outside every range isn't found")
    void numberOutsideEveryRangeIsNotFound() {
        given(operatorRepository.findRangeOwner("01512345678")).willReturn(Optional.empty());

        assertThat(service.findOwnership("01512345678")).isEmpty();
        assertThatThrownBy(() -> service.getOwnership("01512345678"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Phone number 01512345678 is outside every operator's number range");
    }

    private static PortedNumber portedNumber(String phoneNumber, Operator currentOperator) {
        PortedNumber portedNumber = BeanUtils.instantiateClass(PortedNumber.class);
        ReflectionTestUtils.setField(portedNumber, "phoneNumber", phoneNumber);
        ReflectionTestUtils.setField(portedNumber, "currentOperator", currentOperator);
        return portedNumber;
    }
}
