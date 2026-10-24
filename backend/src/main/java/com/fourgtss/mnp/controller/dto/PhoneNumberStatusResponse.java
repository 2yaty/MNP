package com.fourgtss.mnp.controller.dto;

import com.fourgtss.mnp.model.NumberOwnership;

/**
 * Who serves a phone number now. Operators appear by their {@code organization} code.
 *
 * @param rangeOwner    the operator whose number range contains the number
 * @param currentHolder the operator serving the number now
 * @param ported        whether the number is with an operator other than its range owner
 */
public record PhoneNumberStatusResponse(
        String phoneNumber,
        String rangeOwner,
        String currentHolder,
        boolean ported) {

    public static PhoneNumberStatusResponse from(String phoneNumber, NumberOwnership ownership) {
        return new PhoneNumberStatusResponse(
                phoneNumber,
                ownership.rangeOwner().getCode(),
                ownership.currentHolder().getCode(),
                ownership.isPorted());
    }
}
