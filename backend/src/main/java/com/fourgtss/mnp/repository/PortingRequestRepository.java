package com.fourgtss.mnp.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.fourgtss.mnp.model.Operator;
import com.fourgtss.mnp.model.PortingRequest;
import com.fourgtss.mnp.model.PortingStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PortingRequestRepository extends JpaRepository<PortingRequest, UUID> {

    boolean existsByPhoneNumberAndStatus(String phoneNumber, PortingStatus status);

    @EntityGraph(attributePaths = {"recipient", "donor"})
    Optional<PortingRequest> findWithOperatorsById(UUID id);

    /**
     * Moves a pending request to its outcome, only if the caller is its donor. The status condition makes
     * this safe against a concurrent change (another answer, or the timeout job): whichever runs first wins.
     *
     * @return 1 if the request was resolved, 0 if it doesn't exist, isn't the caller's, or isn't pending
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update PortingRequest p
            set p.status = :outcome, p.resolvedAt = :resolvedAt
            where p.id = :id
              and p.donor = :donor
              and p.status = com.fourgtss.mnp.model.PortingStatus.PENDING""")
    int resolvePending(UUID id, Operator donor, PortingStatus outcome, Instant resolvedAt);
}
