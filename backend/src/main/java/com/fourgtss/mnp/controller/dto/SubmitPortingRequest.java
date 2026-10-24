package com.fourgtss.mnp.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body of {@code POST /api/porting-requests}: exactly one phone number. */
public record SubmitPortingRequest(
        @NotNull(message = "is required")
        @Pattern(regexp = "01\\d{9}", message = "must be an 11-digit mobile number starting with 01")
        String phoneNumber) {
}
