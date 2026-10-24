package com.fourgtss.mnp.repository;

import java.util.Optional;

import com.fourgtss.mnp.model.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperatorRepository extends JpaRepository<Operator, Long> {

    /** Case-sensitive: the column uses a binary collation. */
    Optional<Operator> findByCode(String code);

    /** The operator whose number range contains the phone number, if any. */
    @Query("select o from Operator o where :phoneNumber between o.rangeStart and o.rangeEnd")
    Optional<Operator> findRangeOwner(String phoneNumber);
}
