package com.fourgtss.mnp.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fourgtss.mnp.common.BusinessRuleViolationException;
import com.fourgtss.mnp.common.ConflictException;
import com.fourgtss.mnp.common.ForbiddenException;
import com.fourgtss.mnp.common.NotFoundException;
import com.fourgtss.mnp.model.NumberOwnership;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
import com.fourgtss.mnp.repository.PortedNumberRepository;
import com.fourgtss.mnp.repository.PortingRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.IdGenerator;

@Service
public class PortingRequestService {

    /** Unique key that allows one pending request per phone number (see the V1 migration). */
    private static final String PENDING_PHONE_NUMBER_CONSTRAINT = "uk_porting_requests_pending_phone_number";

    private final PortingRequestRepository portingRequestRepository;
    private final PortedNumberRepository portedNumberRepository;
    private final PhoneNumberService phoneNumberService;
    private final IdGenerator idGenerator;
    private final Clock clock;

    public PortingRequestService(PortingRequestRepository portingRequestRepository,
                                 PortedNumberRepository portedNumberRepository, PhoneNumberService phoneNumberService,
                                 IdGenerator idGenerator, Clock clock) {
        this.portingRequestRepository = portingRequestRepository;
        this.portedNumberRepository = portedNumberRepository;
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

    /** A page of the requests the caller may see, newest first (see {@link PortingRequest#isVisibleTo}). */
    @Transactional(readOnly = true)
    public Page<PortingRequest> findVisibleTo(Operator caller, Pageable pageable) {
        return portingRequestRepository.findVisibleTo(caller, pageable);
    }

    /** @throws NotFoundException if the request doesn't exist or the caller can't see it */
    @Transactional(readOnly = true)
    public PortingRequest getVisible(Operator caller, UUID requestId) {
        return portingRequestRepository.findWithOperatorsById(requestId)
                .filter(request -> request.isVisibleTo(caller))
                .orElseThrow(() -> new NotFoundException("Porting request %s not found".formatted(requestId)));
    }

    /**
     * The donor accepts a pending request; the number moves to the recipient.
     *
     * @throws NotFoundException  if the request doesn't exist or the caller can't see it
     * @throws ForbiddenException if the caller isn't the donor
     * @throws ConflictException  if the request isn't pending anymore
     */
    @Transactional
    public PortingRequest accept(Operator caller, UUID requestId) {
        PortingRequest request = resolve(caller, requestId, PortingStatus.ACCEPTED);
        portedNumberRepository.recordCurrentHolder(request.getPhoneNumber(), request.getRecipient().getId());
        return request;
    }

    /**
     * The donor rejects a pending request; the number stays where it is.
     *
     * @throws NotFoundException  if the request doesn't exist or the caller can't see it
     * @throws ForbiddenException if the caller isn't the donor
     * @throws ConflictException  if the request isn't pending anymore
     */
    @Transactional
    public PortingRequest reject(Operator caller, UUID requestId) {
        return resolve(caller, requestId, PortingStatus.REJECTED);
    }

    private PortingRequest resolve(Operator caller, UUID requestId, PortingStatus outcome) {
        boolean resolved = portingRequestRepository.resolvePending(requestId, caller, outcome, now()) == 1;
        PortingRequest request = getVisible(caller, requestId);
        if (resolved) {
            return request;
        }
        if (!request.getDonor().equals(caller)) {
            throw new ForbiddenException("Only the donor can accept or reject porting request %s".formatted(requestId));
        }
        throw new ConflictException("Porting request %s is already %s".formatted(requestId, request.getStatus()));
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
