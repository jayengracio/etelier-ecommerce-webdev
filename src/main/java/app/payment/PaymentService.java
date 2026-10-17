package app.payment;

import app.cart.CartService;
import app.cart.OutOfStockException;
import app.order.Order;
import app.order.OrderItem;
import app.order.OrderRepository;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionCreateParams.LineItem;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData;
import com.stripe.param.checkout.SessionCreateParams.LineItem.PriceData.ProductData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final StripeClient stripe;
    private final OrderRepository orderRepository;
    private final String baseUrl;
    private final CartService cartService;

    public PaymentService(
            StripeClient stripe,
            OrderRepository orderRepository,
            @Value("${app.base-url}") String baseUrl,
            CartService cartService) {
        this.stripe = stripe;
        this.orderRepository = orderRepository;
        this.baseUrl = baseUrl;
        this.cartService = cartService;
    }

    /**
     * Create a Stripe Checkout Session for a PENDING order and return the URL to
     * send the customer to.
     * Not @Transactional on purpose: no database transaction stays open while we
     * wait on Stripe.
     * If Stripe fails, the order is cancelled so it doesn't sit PENDING forever.
     */
    public String startCheckout(Order order) throws StripeException {
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setClientReferenceId(String.valueOf(order.getId()))
                .setSuccessUrl(baseUrl + "/purchase")
                .setCancelUrl(baseUrl + "/checkout?cancelled");

        for (OrderItem item : order.getItems()) {
            params.addLineItem(LineItem.builder()
                    .setQuantity((long) item.getQuantity())
                    .setPriceData(PriceData.builder()
                            .setCurrency("eur")
                            // Stripe wants the smallest unit: €12.50 -> 1250 cents
                            .setUnitAmount(item.getUnitPrice().movePointRight(2).longValueExact())
                            .setProductData(ProductData.builder()
                                    .setName(item.getProduct().getName())
                                    .build())
                            .build())
                    .build());
        }

        Session session;
        try {
            session = stripe.v1().checkout().sessions().create(params.build());
        } catch (StripeException e) {
            log.error("Stripe session for order {} failed", order.getId(), e);
            order.cancel();
            orderRepository.save(order);
            throw e;
        }

        order.setStripeSessionId(session.getId());
        orderRepository.save(order);
        return session.getUrl();
    }

    /**
     * checkout.session.completed: the customer finished paying
     */
    public void handleCheckoutCompleted(Session session) throws StripeException {
        Integer orderId = orderIdOf(session);
        if (orderId == null) {
            return;
        }

        if (!"paid".equals(session.getPaymentStatus())) {
            log.info("Session {} for order {} completed but not paid yet", session.getId(), orderId);
            return;
        }

        try {
            cartService.completePayment(orderId, session.getPaymentIntent());
        } catch (OutOfStockException e) {
            log.warn("Order {} was paid but: {}. Refunding.", orderId, e.getMessage());
            stripe.v1()
                    .refunds()
                    .create(
                            RefundCreateParams.builder()
                                    .setPaymentIntent(session.getPaymentIntent())
                                    .build(),
                            RequestOptions.builder()
                                    .setIdempotencyKey("refund-order-" + orderId)
                                    .build());
            cartService.cancelIfPending(orderId);
        }
    }

    /**
     * checkout.session.expired: never paid (by default 24 hours after the session
     * was created).
     */
    public void handleCheckoutExpired(Session session) {
        Integer orderId = orderIdOf(session);
        if (orderId != null) {
            cartService.cancelIfPending(orderId);
        }
    }

    /**
     * Admin refund of a PAID order. Stripe first, then the database:
     * If the database step fails, refunding again is safe, because the idempotency
     * key makes Stripe
     * return the same refund instead of making a second one.
     */
    public void refund(Order order) throws StripeException {
        stripe.v1()
                .refunds()
                .create(
                        RefundCreateParams.builder()
                                .setPaymentIntent(paymentIntentOf(order))
                                .build(),
                        RequestOptions.builder()
                                .setIdempotencyKey("admin-refund-order-" + order.getId())
                                .build());
        cartService.markRefunded(order.getId());
        log.info("Order {} refunded by an admin", order.getId());
    }

    /**
     * Orders paid before V9 have no PaymentIntent saved, so ask Stripe for the
     * session's.
     */
    private String paymentIntentOf(Order order) throws StripeException {
        if (order.getStripePaymentIntentId() != null) {
            return order.getStripePaymentIntentId();
        }
        return stripe.v1()
                .checkout()
                .sessions()
                .retrieve(order.getStripeSessionId())
                .getPaymentIntent();
    }

    /**
     * The order id we put in the client_reference_id; null for sessions this dop
     * didn't create
     */
    private Integer orderIdOf(Session session) {
        String reference = session.getClientReferenceId();
        if (reference == null) {
            log.info("Ignoring session {}: no order reference", session.getId());
            return null;
        }
        return Integer.valueOf(reference);
    }
}
