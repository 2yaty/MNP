package com.fourgtss.mnp.repository;

import java.util.Optional;

import com.fourgtss.mnp.model.PortedNumber;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortedNumberRepository extends JpaRepository<PortedNumber, String> {

    @EntityGraph(attributePaths = "currentOperator")
    Optional<PortedNumber> findByPhoneNumber(String phoneNumber);
}
