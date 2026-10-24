package com.fourgtss.mnp.controller.dto;

import com.fourgtss.mnp.common.MobileNumber;
import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/porting-requests}: exactly one phone number. */
public record SubmitPortingRequest(
        @NotNull(message = "is required")
        @MobileNumber
        String phoneNumber) {
}
