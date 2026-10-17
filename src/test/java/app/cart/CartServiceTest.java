package app.cart;

import static app.TestData.product;
import static app.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import app.TestContainerConfiguration;
import app.order.Order;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.product.Product;
import app.product.ProductNotFoundException;
import app.product.ProductRepository;
import app.user.User;
import app.user.UserRepository;
import com.stripe.StripeClient;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The real CartService against the real database. Not @Transactional: each service
 * call commits or rolls back on its own, exactly as in the app, and the test checks
 * what was committed. cleanUp() removes the rows afterwards.
 */
@SpringBootTest
@Import(TestContainerConfiguration.class)
@ActiveProfiles("test")
class CartServiceTest {

    private static final String USERNAME = "test-customer";

    @MockitoBean
    StripeClient stripe;

    @Autowired
    CartService cartService;

    @Autowired
    ProductRepository products;

    @Autowired
    CartItemRepository cartItems;

    @Autowired
    OrderRepository orders;

    @Autowired
    UserRepository users;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    JdbcTemplate jdbc;

    private User customer;

    @BeforeEach
    void createCustomer() {
        customer = users.save(user(USERNAME));
    }

    @AfterEach
    void cleanUp() {
        // Orders first: they block deleting users (ON DELETE RESTRICT). order_items and
        // cart_items go with their order, user or product (ON DELETE CASCADE).
        jdbc.update("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'test-%'");
        jdbc.update("DELETE FROM product WHERE type = 'test'");
    }

    private Product save(Product product) {
        return products.save(product);
    }

    private void putInCart(Product product, int quantity) {
        cartItems.save(new CartItem(customer, product, quantity));
    }

    private int stockOf(Product product) {
        return products.findById(product.getId()).orElseThrow().getStock();
    }

    private Order reload(Order order) {
        return orders.findById(order.getId()).orElseThrow();
    }

    /** Checkout plus the webhook: the cart becomes an order and the order is paid. */
    private Order paidOrder() {
        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();
        order.setStripeSessionId("cs_test_" + order.getId());
        orders.save(order);
        cartService.completePayment(order.getId(), "pi_test_" + order.getId());
        return reload(order);
    }

    // addToCart

    @Test
    void addToCartStopsAtTheStock() {
        Product lastOne = save(product("Test Last One", 1));

        assertThat(cartService.addToCart(USERNAME, lastOne.getId())).isTrue();
        assertThat(cartService.addToCart(USERNAME, lastOne.getId())).isFalse();

        assertThat(cartItems.findByUserUsernameOrderById(USERNAME))
                .singleElement()
                .extracting(CartItem::getQuantity)
                .isEqualTo(1);
    }

    @Test
    void hiddenProductsCannotBeAddedToTheCart() {
        Product product = product("Test Hidden", 10);
        product.setActive(false);
        Product hidden = save(product);

        assertThatThrownBy(() -> cartService.addToCart(USERNAME, hidden.getId()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    // createPendingOrder: "Pay with Stripe"

    @Test
    void createPendingOrderCopiesTheCartWithTodaysPricesAndTakesNoStock() {
        Product apple = product("Test Apple", 10);
        apple.setPrice(new BigDecimal("2.50"));
        apple = save(apple);
        Product pear = save(product("Test Pear", 5));
        putInCart(apple, 2);
        putInCart(pear, 1);

        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getTotal()).isEqualByComparingTo("6.00");
        assertThat(order.getItems())
                .extracting(item -> item.getProduct().getName(), item -> item.getQuantity())
                .containsExactlyInAnyOrder(tuple("Test Apple", 2), tuple("Test Pear", 1));
        assertThat(stockOf(apple)).isEqualTo(10);
        assertThat(cartItems.findByUserUsernameOrderById(USERNAME)).hasSize(2);
    }

    @Test
    void createPendingOrderWithAnEmptyCartCreatesNothing() {
        assertThat(cartService.createPendingOrder(USERNAME)).isEmpty();
    }

    @Test
    void createPendingOrderRefusesAProductThatWasHiddenAfterItWasAdded() {
        Product product = save(product("Test Soon Hidden", 10));
        putInCart(product, 1);
        product.setActive(false);
        save(product);

        assertThatThrownBy(() -> cartService.createPendingOrder(USERNAME)).isInstanceOf(OutOfStockException.class);
        assertThat(orders.findByUserUsernameOrderByCreatedAtDesc(USERNAME)).isEmpty();
    }

    @Test
    void createPendingOrderRefusesMoreThanTheStock() {
        Product product = save(product("Test Scarce", 10));
        putInCart(product, 3);
        product.setStock(2);
        save(product);

        assertThatThrownBy(() -> cartService.createPendingOrder(USERNAME)).isInstanceOf(OutOfStockException.class);
        assertThat(orders.findByUserUsernameOrderByCreatedAtDesc(USERNAME)).isEmpty();
    }

    // completePayment: the webhook's checkout.session.completed

    @Test
    void completePaymentTakesStockMarksPaidAndClearsOnlyThePaidProducts() {
        Product apple = save(product("Test Apple", 10));
        Product pear = save(product("Test Pear", 5));
        putInCart(apple, 2);
        putInCart(pear, 1);
        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();
        // Added while the customer was on Stripe's page: not part of this order
        Product addedLater = save(product("Test Added Later", 3));
        putInCart(addedLater, 1);

        cartService.completePayment(order.getId(), "pi_test_1");

        assertThat(stockOf(apple)).isEqualTo(8);
        assertThat(stockOf(pear)).isEqualTo(4);
        assertThat(stockOf(addedLater)).isEqualTo(3);
        Order paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getStripePaymentIntentId()).isEqualTo("pi_test_1");
        assertThat(cartItems.findByUserUsernameOrderById(USERNAME))
                .extracting(item -> item.getProduct().getId())
                .containsExactly(addedLater.getId());
    }

    @Test
    void aSecondDeliveryOfThePaymentChangesNothing() {
        Product apple = save(product("Test Apple", 10));
        putInCart(apple, 2);
        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();

        cartService.completePayment(order.getId(), "pi_test_1");
        cartService.completePayment(order.getId(), "pi_test_2");

        assertThat(stockOf(apple)).isEqualTo(8);
        assertThat(reload(order).getStripePaymentIntentId()).isEqualTo("pi_test_1");
    }

    @Test
    void completePaymentForAnUnknownOrderDoesNothing() {
        assertThatNoException().isThrownBy(() -> cartService.completePayment(999_999, "pi_test_1"));
    }

    @Test
    void runningOutOfStockAtPaymentRollsEverythingBack() {
        // Created first, so it has the lower id: completePayment takes its stock
        // before it reaches the scarce product, and the rollback has to undo that.
        Product plenty = save(product("Test Plenty", 10));
        Product scarce = save(product("Test Scarce", 5));
        putInCart(plenty, 2);
        putInCart(scarce, 3);
        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();
        // Someone else bought most of it while this customer was paying
        scarce.setStock(1);
        save(scarce);

        assertThatThrownBy(() -> cartService.completePayment(order.getId(), "pi_test_1"))
                .isInstanceOf(OutOfStockException.class);

        assertThat(stockOf(plenty)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        Order unpaid = reload(order);
        assertThat(unpaid.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(unpaid.getStripePaymentIntentId()).isNull();
        assertThat(cartItems.findByUserUsernameOrderById(USERNAME)).hasSize(2);
    }

    // markRefunded: after Stripe confirmed an admin refund

    @Test
    void markRefundedPutsTheStockBackOnce() {
        Product apple = save(product("Test Apple", 10));
        putInCart(apple, 2);
        Order order = paidOrder();
        assertThat(stockOf(apple)).isEqualTo(8);

        cartService.markRefunded(order.getId());
        cartService.markRefunded(order.getId());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(apple)).isEqualTo(10);
    }

    @Test
    void markRefundedIgnoresAnOrderThatIsNotPaid() {
        Product apple = save(product("Test Apple", 10));
        putInCart(apple, 2);
        Order order = cartService.createPendingOrder(USERNAME).orElseThrow();

        cartService.markRefunded(order.getId());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(apple)).isEqualTo(10);
    }

    @Test
    void markRefundedWaitsForARefundOfTheSameOrderThatIsStillRunning() throws Exception {
        Product apple = save(product("Test Apple", 10));
        putInCart(apple, 2);
        Order order = paidOrder();

        // The first click: lock the order and refund it, but don't commit yet.
        // The second click starts meanwhile and must wait for the lock; then it sees
        // REFUNDED and does nothing. Without the lock it still sees PAID (the first
        // click hasn't committed) and puts the stock back.
        CompletableFuture<Void> secondClick = tx.execute(status -> {
            orders.findLockedById(order.getId()).orElseThrow().refund();
            CompletableFuture<Void> click = CompletableFuture.runAsync(() -> cartService.markRefunded(order.getId()));
            pause(500);
            return click;
        });
        secondClick.get(10, TimeUnit.SECONDS);

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        // Only the second click could have touched the stock, and it mustn't have
        assertThat(stockOf(apple)).isEqualTo(8);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
