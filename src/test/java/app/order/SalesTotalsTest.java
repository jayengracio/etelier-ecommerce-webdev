package app.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

public class SalesTotalsTest {

    @Test
    void nullRevenueBecomesZero() {
        // SUM over no rows is NULL in SQL. e.g., no sales in the last 7 days
        assertThat(new SalesTotals(0, null).revenue()).isEqualByComparingTo("0");
    }

    // isEqualByComparingTo instead of isEqualTo: BigDecimal.equals also compares
    // scale, so 0 and 0.00 are not equal.
    // The database returns NUMERIC(10,2) value like 0.00.
    // Comparing with compareTo ignores scale, so the test checks the amount, not
    // the formatting.
    @Test
    void revenueIsKeptWhenPresent() {
        assertThat(new SalesTotals(3, new BigDecimal("59.97")).revenue()).isEqualByComparingTo("59.97");
    }
}
