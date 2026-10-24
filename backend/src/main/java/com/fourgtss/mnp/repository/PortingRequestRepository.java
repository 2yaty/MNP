package com.fourgtss.mnp.repository;

import java.util.UUID;

import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortingRequestRepository extends JpaRepository<PortingRequest, UUID> {

    boolean existsByPhoneNumberAndStatus(String phoneNumber, PortingStatus status);
}
