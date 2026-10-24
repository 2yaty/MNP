package com.fourgtss.mnp.controller;

import java.net.URI;
import java.util.UUID;

import com.fourgtss.mnp.auth.AuthenticatedOperator;
import com.fourgtss.mnp.controller.dto.PageResponse;
import com.fourgtss.mnp.controller.dto.PortingRequestResponse;
import com.fourgtss.mnp.controller.dto.SubmitPortingRequest;
import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.service.PortingRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/porting-requests")
public class PortingRequestController {

    static final int MAX_PAGE_SIZE = 100;

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

    /** The caller's own requests in every status, plus everyone's accepted ones; newest first, one page at a time. */
    @GetMapping
    public PageResponse<PortingRequestResponse> list(
            @AuthenticatedOperator Operator caller,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return PageResponse.from(portingRequestService.findVisibleTo(caller, PageRequest.of(page, size)),
                PortingRequestResponse::from);
    }

    /** {@code 404} when the request doesn't exist or the caller isn't allowed to see it. */
    @GetMapping("/{id}")
    public PortingRequestResponse get(@AuthenticatedOperator Operator caller, @PathVariable UUID id) {
        return PortingRequestResponse.from(portingRequestService.getVisible(caller, id));
    }

    /** Only the donor can accept, and only while the request is pending. */
    @PostMapping("/{id}/accept")
    public PortingRequestResponse accept(@AuthenticatedOperator Operator caller, @PathVariable UUID id) {
        return PortingRequestResponse.from(portingRequestService.accept(caller, id));
    }

    /** Only the donor can reject, and only while the request is pending. */
    @PostMapping("/{id}/reject")
    public PortingRequestResponse reject(@AuthenticatedOperator Operator caller, @PathVariable UUID id) {
        return PortingRequestResponse.from(portingRequestService.reject(caller, id));
    }
}
