package com.fourgtss.mnp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.fourgtss.mnp.common.BusinessRuleViolationException;
import com.fourgtss.mnp.common.ConflictException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
import com.fourgtss.mnp.repository.PortingRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

@Service
public class PortingRequestService {

    /** Unique key that allows one pending request per phone number (see the V1 migration). */
    private static final String PENDING_PHONE_NUMBER_CONSTRAINT = "uk_porting_requests_pending_phone_number";

    private final PortingRequestRepository portingRequestRepository;
    private final PhoneNumberService phoneNumberService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PortingRequestService(PortingRequestRepository portingRequestRepository,
                                 PhoneNumberService phoneNumberService, IdGenerator idGenerator, Clock clock) {
        this.portingRequestRepository = portingRequestRepository;
        this.phoneNumberService = phoneNumberService;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    /**
     * Submits a request to port the number to the recipient. The donor is whoever holds the number now.
     *
     * @throws BusinessRuleViolationException if the number is outside every range or the recipient already holds it
     * @throws ConflictException              if the number already has a pending request
     */
    @Transactional
    public PortingRequest submit(Operator recipient, String phoneNumber) {
        Operator donor = phoneNumberService.findOwnership(phoneNumber)
                .map(NumberOwnership::currentHolder)
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Phone number %s is outside every operator's number range".formatted(phoneNumber)));

        if (donor.equals(recipient)) {
            throw new BusinessRuleViolationException(
                    "%s already holds phone number %s".formatted(recipient.getName(), phoneNumber));
        }
        if (portingRequestRepository.existsByPhoneNumberAndStatus(phoneNumber, PortingStatus.PENDING)) {
            throw pendingRequestExists(phoneNumber);
        }

        PortingRequest request = PortingRequest.submit(idGenerator.generateId(), phoneNumber, recipient, donor, now());
        try {
            return portingRequestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException exception) {
            // Another request for the same number was inserted between the check above and this insert.
            if (violates(exception, PENDING_PHONE_NUMBER_CONSTRAINT)) {
                throw pendingRequestExists(phoneNumber);
            }
            throw exception;
        }
    }

    /** Millisecond precision, matching the DATETIME(3) columns. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private static ConflictException pendingRequestExists(String phoneNumber) {
        return new ConflictException("Phone number %s already has a pending porting request".formatted(phoneNumber));
    }

    private static boolean violates(DataIntegrityViolationException exception, String constraint) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraint);
    }
}
