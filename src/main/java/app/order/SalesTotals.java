package app.order;

import java.math.BigDecimal;

/**
 * How many orders and how much money. SUM over no rows is NULL in SQL,
 * so null becomes 0.
 */
public record SalesTotals(long orders, BigDecimal revenue) {

    public SalesTotals {
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }
    }
}
