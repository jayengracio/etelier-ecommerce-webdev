package app.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

import app.user.User;

class OrderTest {

    /** An order already in the given status, as if it got there through the normal flow. */
    private static Order orderIn(OrderStatus status) {
        Order order = new Order(new User("Phil", "hash"));
        order.setStatus(status);
        return order;
    }

    @Test
    void newOrderIsPending() {
        assertThat(new Order(new User("Phil", "hash")).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // markPaid: the webhook confirms payment

    @Test
    void markPaidMovesPendingToPaidAndSavesThePaymentIntent() {
        Order order = orderIn(OrderStatus.PENDING);

        order.markPaid("pi_123");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getStripePaymentIntentId()).isEqualTo("pi_123");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = "PENDING")
    void markPaidRefusesEveryOtherStatus(OrderStatus status) {
        Order order = orderIn(status);

        assertThatIllegalStateException().isThrownBy(() -> order.markPaid("pi_123"));
        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getStripePaymentIntentId()).isNull();
    }

    // markShipped: admin ships a paid order

    @Test
    void markShippedMovesPaidToShipped() {
        Order order = orderIn(OrderStatus.PAID);

        order.markShipped();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = "PAID")
    void markShippedRefusesEveryOtherStatus(OrderStatus status) {
        Order order = orderIn(status);

        assertThatIllegalStateException().isThrownBy(order::markShipped);
        assertThat(order.getStatus()).isEqualTo(status);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void onlyPaidOrdersAreShippable(OrderStatus status) {
        assertThat(orderIn(status).isShippable()).isEqualTo(status == OrderStatus.PAID);
    }

    // cancel: the Checkout Session expired, or stock ran out

    @Test
    void cancelMovesPendingToCancelled() {
        Order order = orderIn(OrderStatus.PENDING);

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = "PENDING")
    void cancelRefusesEveryOtherStatus(OrderStatus status) {
        Order order = orderIn(status);

        assertThatIllegalStateException().isThrownBy(order::cancel);
        assertThat(order.getStatus()).isEqualTo(status);
    }

    // refund: admin refunds a paid Stripe order

    @Test
    void refundMovesPaidStripeOrderToRefunded() {
        Order order = orderIn(OrderStatus.PAID);
        order.setStripeSessionId("cs_test_123");

        order.refund();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void refundRefusesPaidOrderWithoutStripeSession() {
        Order order = orderIn(OrderStatus.PAID);

        assertThat(order.isRefundable()).isFalse();
        assertThatIllegalStateException().isThrownBy(order::refund);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = Mode.EXCLUDE, names = "PAID")
    void refundRefusesEveryOtherStatus(OrderStatus status) {
        Order order = orderIn(status);
        order.setStripeSessionId("cs_test_123");

        assertThat(order.isRefundable()).isFalse();
        assertThatIllegalStateException().isThrownBy(order::refund);
        assertThat(order.getStatus()).isEqualTo(status);
    }
}
