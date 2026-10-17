package app.payment;

import static app.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.TestContainerConfiguration;
import app.TestData;
import app.cart.CartService;
import app.order.Order;
import app.order.OrderItem;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.product.Product;
import app.product.ProductRepository;
import app.user.User;
import app.user.UserRepository;
import com.stripe.Stripe;
import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.service.RefundService;
import com.stripe.service.V1Services;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The Stripe webhook and the admin refund, through HTTP, against the real database.
 * Only Stripe itself is fake: the StripeClient mock checks signatures with the real
 * code and records the refunds we ask for.
 *
 * MockMvc is built by hand instead of with @AutoConfigureMockMvc, so this class keeps
 * the same annotations as CartServiceTest and shares its Spring context and container.
 */
@SpringBootTest
@Import(TestContainerConfiguration.class)
@ActiveProfiles("test")
class PaymentWebTest {

    private static final String USERNAME = "test-customer";

    @MockitoBean
    StripeClient stripe;

    @Autowired
    WebApplicationContext context;

    // The secret the app really checks against: whsec_test_dummy from
    // application-test.properties, unless a STRIPE_WEBHOOK_SECRET environment
    // variable is set, because environment variables beat every properties file
    @Autowired
    StripeProperties stripeProperties;

    @Autowired
    CartService cartService;

    @Autowired
    ProductRepository products;

    @Autowired
    OrderRepository orders;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private MockMvc mvc;
    private RefundService refunds;
    private User customer;

    @BeforeEach
    void setUp() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // The mock returns null for everything, so the webhook would get no Event.
        // Let it run Stripe's real signature check instead.
        when(stripe.constructEvent(any(), any(), any()))
                .thenAnswer(
                        call -> Webhook.constructEvent(call.getArgument(0), call.getArgument(1), call.getArgument(2)));

        // stripe.v1().refunds().create(...): mock each link of the chain
        refunds = mock(RefundService.class);
        V1Services v1 = mock(V1Services.class);
        when(stripe.v1()).thenReturn(v1);
        when(v1.refunds()).thenReturn(refunds);

        customer = users.save(TestData.user(USERNAME));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'test-%'");
        jdbc.update("DELETE FROM product WHERE type = 'test'");
    }

    // ---- helpers ----

    /** What "Pay with Stripe" leaves behind: a PENDING order with a Checkout Session. */
    private Order pendingOrder(Product product, int quantity) {
        Order order = new Order(customer);
        order.addItem(new OrderItem(product, quantity, product.getPrice()));
        order.setTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        order = orders.save(order);
        order.setStripeSessionId("cs_test_" + order.getId());
        return orders.save(order);
    }

    private Order paidOrder(Product product, int quantity) {
        Order order = pendingOrder(product, quantity);
        cartService.completePayment(order.getId(), "pi_test_" + order.getId());
        return reload(order);
    }

    /** A logged-in admin, without a database lookup (Spring Security's user(), not TestData's). */
    private static RequestPostProcessor asAdmin() {
        return user("owner").roles("ADMIN");
    }

    private Order reload(Order order) {
        return orders.findById(order.getId()).orElseThrow();
    }

    private int stockOf(Product product) {
        return products.findById(product.getId()).orElseThrow().getStock();
    }

    /** An event as Stripe sends it, for a checkout session that belongs to the order. */
    private static String event(String type, Order order, String apiVersion) {
        return """
                {
                  "id": "evt_test_%1$d",
                  "object": "event",
                  "api_version": "%2$s",
                  "type": "%3$s",
                  "data": {
                    "object": {
                      "id": "cs_test_%1$d",
                      "object": "checkout.session",
                      "client_reference_id": "%1$d",
                      "payment_status": "paid",
                      "payment_intent": "pi_test_%1$d"
                    }
                  }
                }
                """.formatted(order.getId(), apiVersion, type);
    }

    private static String event(String type, Order order) {
        return event(type, order, Stripe.API_VERSION);
    }

    /** Stripe-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256 of "<t>.<payload>"> */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = hmac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private ResultActions deliver(String payload, String signature) throws Exception {
        return mvc.perform(post("/stripe/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", signature)
                .content(payload));
    }

    private ResultActions deliver(String payload) throws Exception {
        return deliver(payload, sign(payload, stripeProperties.webhookSecret()));
    }

    // ---- the signature ----

    @Test
    void aSignedEventWeDoNotHandleGets200() throws Exception {
        Order order = pendingOrder(products.save(product("Test Apple", 10)), 1);

        deliver(event("payment_intent.created", order)).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void aBadSignatureGets400AndChangesNothing() throws Exception {
        Product apple = products.save(product("Test Apple", 10));
        Order order = pendingOrder(apple, 2);
        String payload = event("checkout.session.completed", order);

        deliver(payload, sign(payload, "whsec_someone_else")).andExpect(status().isBadRequest());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(stockOf(apple)).isEqualTo(10);
    }

    @Test
    void aChangedPayloadNoLongerMatchesItsSignature() throws Exception {
        Order order = pendingOrder(products.save(product("Test Apple", 10)), 1);
        String signed = event("payment_intent.created", order);
        String tampered = signed.replace("payment_intent.created", "checkout.session.completed");

        deliver(tampered, sign(signed, stripeProperties.webhookSecret())).andExpect(status().isBadRequest());
    }

    @Test
    void aMissingSignatureHeaderGets400() throws Exception {
        // @RequestHeader is required, so Spring refuses it before the controller runs
        mvc.perform(post("/stripe/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- checkout.session.completed / expired ----

    @Test
    void aCompletedCheckoutDeliveredTwiceTakesTheStockOnce() throws Exception {
        Product apple = products.save(product("Test Apple", 10));
        Order order = pendingOrder(apple, 2);
        String payload = event("checkout.session.completed", order);

        // Stripe retries when it doesn't get a 2xx in time, so the same event can arrive twice
        deliver(payload).andExpect(status().isOk());
        deliver(payload).andExpect(status().isOk());

        Order paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paid.getStripePaymentIntentId()).isEqualTo("pi_test_" + order.getId());
        assertThat(stockOf(apple)).isEqualTo(8);
        verifyNoInteractions(refunds);
    }

    @Test
    void anEventFromAnotherApiVersionIsStillRead() throws Exception {
        // stripe listen sends the account's default version; the controller falls back
        // to deserializeUnsafe() when it differs from the library's
        Product apple = products.save(product("Test Apple", 10));
        Order order = pendingOrder(apple, 2);

        deliver(event("checkout.session.completed", order, "2024-06-20")).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(apple)).isEqualTo(8);
    }

    @Test
    void runningOutOfStockAtPaymentRefundsAndCancels() throws Exception {
        Product scarce = products.save(product("Test Scarce", 5));
        Order order = pendingOrder(scarce, 3);
        // Someone else bought most of it while this customer was on Stripe's page
        scarce.setStock(1);
        products.save(scarce);

        deliver(event("checkout.session.completed", order)).andExpect(status().isOk());

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refunds).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_test_" + order.getId());
        // A fixed key: if Stripe delivers the event again, it returns the same refund
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("refund-order-" + order.getId());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(stockOf(scarce)).isEqualTo(1);
    }

    @Test
    void anExpiredCheckoutCancelsThePendingOrder() throws Exception {
        Order order = pendingOrder(products.save(product("Test Apple", 10)), 1);

        deliver(event("checkout.session.expired", order)).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ---- the admin refund: POST /orders/{id}/refund ----

    @Test
    void anAdminRefundAsksStripeFirstThenPutsTheStockBack() throws Exception {
        Product apple = products.save(product("Test Apple", 10));
        Order order = paidOrder(apple, 2);
        assertThat(stockOf(apple)).isEqualTo(8);

        mvc.perform(post("/orders/{id}/refund", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?refunded"));

        ArgumentCaptor<RefundCreateParams> params = ArgumentCaptor.forClass(RefundCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(refunds).create(params.capture(), options.capture());
        assertThat(params.getValue().getPaymentIntent()).isEqualTo("pi_test_" + order.getId());
        // A different prefix from the automatic refund, so the two can never collide
        assertThat(options.getValue().getIdempotencyKey()).isEqualTo("admin-refund-order-" + order.getId());

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.REFUNDED);
        assertThat(stockOf(apple)).isEqualTo(10);
    }

    @Test
    void whenStripeFailsTheOrderStaysPaid() throws Exception {
        Product apple = products.save(product("Test Apple", 10));
        Order order = paidOrder(apple, 2);
        when(refunds.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                .thenThrow(new ApiConnectionException("Stripe is unreachable"));

        mvc.perform(post("/orders/{id}/refund", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + order.getId() + "?refundError"));

        assertThat(reload(order).getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(stockOf(apple)).isEqualTo(8);
    }
}
