package app.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The stripe.* settings from application.properties, bound once at startup.
 * Fails startup if the keys are missing, instead of failing at the first
 * checkout.
 */
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(String secretKey, String webhookSecret) {
    public StripeProperties {
        if (secretKey == null || !secretKey.startsWith("sk_")) {
            throw new IllegalStateException("STRIPE_SECRET_KEY is not set (expected sk_test_...)");
        }

        if (webhookSecret == null || !webhookSecret.startsWith("whsec_")) {
            throw new IllegalStateException("STRIPE_WEBHOOK_SECRET is not set (expected whsec_...)");
        }
    }
}
