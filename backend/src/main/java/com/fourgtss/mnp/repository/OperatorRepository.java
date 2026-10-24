package com.fourgtss.mnp.repository;

import java.util.Optional;

import com.fourgtss.mnp.model.Operator;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatorRepository extends JpaRepository<Operator, Long> {

    /** Case-sensitive: the column uses a binary collation. */
    Optional<Operator> findByCode(String code);
}
