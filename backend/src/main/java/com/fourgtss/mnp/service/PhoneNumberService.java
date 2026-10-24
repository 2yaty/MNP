package com.fourgtss.mnp.service;

import java.util.Optional;

import com.fourgtss.mnp.common.NotFoundException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortedNumber;
import com.fourgtss.mnp.repository.OperatorRepository;
import com.fourgtss.mnp.repository.PortedNumberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneNumberService {

    private final OperatorRepository operatorRepository;
    private final PortedNumberRepository portedNumberRepository;

    public PhoneNumberService(OperatorRepository operatorRepository, PortedNumberRepository portedNumberRepository) {
        this.operatorRepository = operatorRepository;
        this.portedNumberRepository = portedNumberRepository;
    }

    /**
     * Finds who owns the number's range and who holds it now.
     *
     * @return empty when the number is outside every operator's range
     */
    @Transactional(readOnly = true)
    public Optional<NumberOwnership> findOwnership(String phoneNumber) {
        return operatorRepository.findRangeOwner(phoneNumber).map(rangeOwner -> {
            Operator currentHolder = portedNumberRepository.findByPhoneNumber(phoneNumber)
                    .map(PortedNumber::getCurrentOperator)
                    .orElse(rangeOwner);
            return new NumberOwnership(rangeOwner, currentHolder);
        });
    }

    /** @throws NotFoundException if the number is outside every operator's range */
    @Transactional(readOnly = true)
    public NumberOwnership getOwnership(String phoneNumber) {
        return findOwnership(phoneNumber).orElseThrow(() -> new NotFoundException(
                "Phone number %s is outside every operator's number range".formatted(phoneNumber)));
    }
}
