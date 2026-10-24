package com.fourgtss.mnp.controller;

import java.net.URI;

import com.fourgtss.mnp.auth.AuthenticatedOperator;
import com.fourgtss.mnp.controller.dto.PortingRequestResponse;
import com.fourgtss.mnp.controller.dto.SubmitPortingRequest;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.service.PortingRequestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/porting-requests")
public class PortingRequestController {

    private final PortingRequestService portingRequestService;

    public PortingRequestController(PortingRequestService portingRequestService) {
        this.portingRequestService = portingRequestService;
    }

    /** The calling operator is the recipient. Returns right away; the donor answers later. */
    @PostMapping
    public ResponseEntity<PortingRequestResponse> submit(@AuthenticatedOperator Operator recipient,
                                                         @Valid @RequestBody SubmitPortingRequest request) {
        PortingRequest portingRequest = portingRequestService.submit(recipient, request.phoneNumber());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(portingRequest.getId())
                .toUri();
        return ResponseEntity.created(location).body(PortingRequestResponse.from(portingRequest));
    }
}
