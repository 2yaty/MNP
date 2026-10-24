package com.fourgtss.mnp.repository;

import java.util.Optional;

import com.fourgtss.mnp.model.PortedNumber;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PortedNumberRepository extends JpaRepository<PortedNumber, String> {

    @EntityGraph(attributePaths = "currentOperator")
    Optional<PortedNumber> findByPhoneNumber(String phoneNumber);

    /** Records the operator now holding the number: inserts the first time, updates on later ports. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(nativeQuery = true, value = """
            INSERT INTO ported_numbers (phone_number, current_operator_id)
            VALUES (:phoneNumber, :operatorId) AS new_holder
            ON DUPLICATE KEY UPDATE current_operator_id = new_holder.current_operator_id""")
    void recordCurrentHolder(String phoneNumber, Long operatorId);
}
