package app.order;

/**
 * One row of "SELECT status, COUNT(*) ... GROUP BY status".
 */
public record StatusCount(OrderStatus status, long count) {}
