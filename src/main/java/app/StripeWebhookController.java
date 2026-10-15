package app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;

@RestController
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeClient stripe;
    private final StripeProperties stripeProperties;
    private final PaymentService paymentService;

    public StripeWebhookController(StripeClient stripe, StripeProperties stripeProperties,
            PaymentService paymentService) {
        this.stripe = stripe;
        this.stripeProperties = stripeProperties;
        this.paymentService = paymentService;
    }

    /**
     * Stripe calls this, not a browser. The signature is the authentication
     * Only Stripe (or stripe listen) knows the webhook secret.
     * A 2xx tells Stripe "handled".
     * Anything else, including exception, makes it retry later.
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<Void> handle(@RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) throws StripeException {
        Event event;
        try {
            event = stripe.constructEvent(payload, signature, stripeProperties.webhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Rejected webhook with a bad signature");
            return ResponseEntity.badRequest().build();
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> paymentService.handleCheckoutCompleted(sessionOf(event));
            case "checkout.session.expired" -> paymentService.handleCheckoutExpired(sessionOf(event));
            default -> {
            } // payment_intent.*, charge.*, ...: not needed
        }

        return ResponseEntity.ok().build();
    }

    /**
     * getObject() is empty when the event's API version differs from this library's
     * (strip listen uses your account's default version).
     * The Session fields we read are the same across versions,
     * so fall back to deserialzeUnsafe().
     */
    private static Session sessionOf(Event event) throws StripeException {
        EventDataObjectDeserializer data = event.getDataObjectDeserializer();
        return (Session) (data.getObject().isPresent() ? data.getObject().get() : data.deserializeUnsafe());
    }
}
