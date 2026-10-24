package com.fourgtss.mnp.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fourgtss.mnp.TestcontainersConfiguration;
import com.fourgtss.mnp.model.Operator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class OperatorRepositoryTest {

    @Autowired
    private OperatorRepository operatorRepository;

    @Test
    @DisplayName("Seed data has the three operators with their number ranges")
    void seedsOperatorsWithRanges() {
        assertThat(operatorRepository.findAll())
                .extracting(Operator::getCode, Operator::getRangeStart, Operator::getRangeEnd)
                .containsExactlyInAnyOrder(
                        tuple("vodafone", "01000000000", "01099999999"),
                        tuple("etisalat", "01100000000", "01199999999"),
                        tuple("orange", "01200000000", "01299999999"));
    }

    @Test
    @DisplayName("US-AUTH-1-AC1 findByCode finds an operator by its header value")
    void findsOperatorByCode() {
        assertThat(operatorRepository.findByCode("vodafone"))
                .hasValueSatisfying(operator -> assertThat(operator.getName()).isEqualTo("Vodafone"));
    }

    @Test
    @DisplayName("US-AUTH-1-AC4 findByCode is case-sensitive")
    void findByCodeIsCaseSensitive() {
        assertThat(operatorRepository.findByCode("Vodafone")).isEmpty();
        assertThat(operatorRepository.findByCode("VODAFONE")).isEmpty();
    }
}
