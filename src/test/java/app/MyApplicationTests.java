package app;

import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestContainerConfiguration.class)
@ActiveProfiles("test")
class MyApplicationTests {

    @MockitoBean
    StripeClient stripe;

    @Test
    void contextLoad() {}
}
