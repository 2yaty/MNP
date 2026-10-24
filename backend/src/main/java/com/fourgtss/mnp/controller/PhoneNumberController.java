package com.fourgtss.mnp.controller;

import com.fourgtss.mnp.common.MobileNumber;
import com.fourgtss.mnp.controller.dto.PhoneNumberStatusResponse;
import com.fourgtss.mnp.service.PhoneNumberService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/phone-numbers")
public class PhoneNumberController {

    private final PhoneNumberService phoneNumberService;

    public PhoneNumberController(PhoneNumberService phoneNumberService) {
        this.phoneNumberService = phoneNumberService;
    }

    /** Any operator can look up any number. {@code 404} when it's outside every operator's range. */
    @GetMapping("/{phoneNumber}")
    public PhoneNumberStatusResponse getStatus(@PathVariable @MobileNumber String phoneNumber) {
        return PhoneNumberStatusResponse.from(phoneNumber, phoneNumberService.getOwnership(phoneNumber));
    }
}
