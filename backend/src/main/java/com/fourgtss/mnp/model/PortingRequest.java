package com.fourgtss.mnp.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/** A recipient's request to take over a phone number from its current holder (the donor). */
@Entity
@Table(name = "porting_requests")
public class PortingRequest implements Persistable<UUID> {

    /** Assigned by the application (UUID v7), so {@link #isNew()} tells Spring Data to insert, not merge. */
    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false, updatable = false, length = 11)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_operator_id", nullable = false, updatable = false)
    private Operator recipient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "donor_operator_id", nullable = false, updatable = false)
    private Operator donor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PortingStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Transient
    private boolean isNew = true;

    protected PortingRequest() {
        // for JPA
    }

    private PortingRequest(UUID id, String phoneNumber, Operator recipient, Operator donor, Instant createdAt) {
        this.id = id;
        this.phoneNumber = phoneNumber;
        this.recipient = recipient;
        this.donor = donor;
        this.status = PortingStatus.PENDING;
        this.createdAt = createdAt;
    }

    /** Creates a new request in {@link PortingStatus#PENDING}. */
    public static PortingRequest submit(UUID id, String phoneNumber, Operator recipient, Operator donor,
                                        Instant createdAt) {
        return new PortingRequest(id, phoneNumber, recipient, donor, createdAt);
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Operator getRecipient() {
        return recipient;
    }

    public Operator getDonor() {
        return donor;
    }

    public PortingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        isNew = false;
    }
}
