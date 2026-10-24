package com.fourgtss.mnp.controller.dto;

import java.time.Instant;
import java.util.UUID;

import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;

/** A porting request as the API shows it. Operators appear by their {@code organization} code. */
public record PortingRequestResponse(
        UUID id,
        String phoneNumber,
        String recipient,
        String donor,
        PortingStatus status,
        Instant createdAt,
        Instant resolvedAt) {

    public static PortingRequestResponse from(PortingRequest request) {
        return new PortingRequestResponse(
                request.getId(),
                request.getPhoneNumber(),
                request.getRecipient().getCode(),
                request.getDonor().getCode(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getResolvedAt());
    }
}
