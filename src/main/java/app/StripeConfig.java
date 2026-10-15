package app;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.stripe.StripeClient;

@Configuration
@EnableConfigurationProperties(StripeProperties.class)
public class StripeConfig {
    
    @Bean
    StripeClient stripeClient(StripeProperties stripe) {
        return new StripeClient(stripe.secretKey());
    }
}
