package com.fourgtss.mnp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** The final state of a phone number that has been ported: who holds it now. */
@Entity
@Table(name = "ported_numbers")
public class PortedNumber {

    @Id
    @Column(name = "phone_number", length = 11)
    private String phoneNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_operator_id", nullable = false)
    private Operator currentOperator;

    protected PortedNumber() {
        // for JPA
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Operator getCurrentOperator() {
        return currentOperator;
    }
}
