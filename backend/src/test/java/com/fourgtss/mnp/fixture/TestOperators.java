package com.fourgtss.mnp.fixture;

import com.fourgtss.mnp.model.Operator;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** The seeded operators as detached entities, for tests that don't touch the database. */
public final class TestOperators {

    public static final Operator VODAFONE = operator(1L, "vodafone", "Vodafone", "01000000000", "01099999999");
    public static final Operator ETISALAT = operator(2L, "etisalat", "Etisalat", "01100000000", "01199999999");
    public static final Operator ORANGE = operator(3L, "orange", "Orange", "01200000000", "01299999999");

    private TestOperators() {
    }

    private static Operator operator(long id, String code, String name, String rangeStart, String rangeEnd) {
        Operator operator = BeanUtils.instantiateClass(Operator.class);
        ReflectionTestUtils.setField(operator, "id", id);
        ReflectionTestUtils.setField(operator, "code", code);
        ReflectionTestUtils.setField(operator, "name", name);
        ReflectionTestUtils.setField(operator, "rangeStart", rangeStart);
        ReflectionTestUtils.setField(operator, "rangeEnd", rangeEnd);
        return operator;
    }
}
