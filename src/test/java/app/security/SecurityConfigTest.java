package app.security;

import static app.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.TestContainerConfiguration;
import app.product.Product;
import app.product.ProductRepository;
import com.stripe.StripeClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * The rules in SecurityConfig, through the real filter chain. Same annotations as
 * the other @SpringBootTest classes, so the context and container are shared.
 */
@SpringBootTest
@Import(TestContainerConfiguration.class)
@ActiveProfiles("test")
class SecurityConfigTest {

    @MockitoBean
    StripeClient stripe;

    @Autowired
    WebApplicationContext context;

    @Autowired
    ProductRepository products;

    @Autowired
    JdbcTemplate jdbc;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM product WHERE type = 'test'");
    }

    // A logged-in user without a database lookup (Spring Security's user())
    private static RequestPostProcessor asCustomer() {
        return user("test-customer").roles("USER");
    }

    private static RequestPostProcessor asAdmin() {
        return user("owner").roles("ADMIN");
    }

    private boolean isActive(Product product) {
        return products.findById(product.getId()).orElseThrow().isActive();
    }

    // ---- admin URLs ----
    // The ids don't need to exist: security refuses the request before the controller
    // looks anything up.

    // ---- static files ----

    @Test
    void anonymousVisitorsGetTheTailwindStylesheet() throws Exception {
        // Maven builds app.css before the tests run (frontend-maven-plugin). It must be
        // public, or the login page itself would be unstyled.
        mvc.perform(get("/app.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"))
                .andExpect(content().string(containsString("tailwindcss")));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /owners",
        "GET,  /view/orders",
        "GET,  /edit/1",
        "GET,  /orders/1",
        "POST, /product/add",
        "POST, /edit/1/active",
        "POST, /edit/product",
        "POST, /orders/1/ship",
        "POST, /orders/1/refund"
    })
    void anonymousVisitorsAreSentToTheLoginPage(String method, String url) throws Exception {
        // With a CSRF token, so it's the missing login that gets refused, not the token
        mvc.perform(request(HttpMethod.valueOf(method), url).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void anAnonymousScriptAddIsSentToTheLoginPageToo() throws Exception {
        // code.js relies on this: fetch follows the redirect and the page then goes to /login.
        // A 401 here would leave the customer looking at an error toast instead.
        mvc.perform(post("/cart/add/1")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource({
        "GET,  /owners",
        "GET,  /view/orders",
        "GET,  /edit/1",
        "GET,  /orders/1",
        "POST, /product/add",
        "POST, /edit/1/active",
        "POST, /edit/product",
        "POST, /orders/1/ship",
        "POST, /orders/1/refund"
    })
    void customersAreRefused(String method, String url) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), url).with(asCustomer()).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/owners", "/view/orders"})
    void adminsGetIn(String url) throws Exception {
        // Proves the refusals above are about the role, not a page that's broken anyway
        mvc.perform(get(url).with(asAdmin())).andExpect(status().isOk());
    }

    // ---- POST /edit/{id}/active ----

    @Test
    void aCustomerCannotHideAProduct() throws Exception {
        Product product = products.save(product("Test Visible", 10));

        mvc.perform(post("/edit/{id}/active", product.getId())
                        .param("active", "false")
                        .with(asCustomer())
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(isActive(product)).isTrue();
    }

    @Test
    void anAdminCanHideAProduct() throws Exception {
        // The same request as above with the right role, so the refusals can't be
        // passing only because the request itself is broken
        Product product = products.save(product("Test Visible", 10));

        mvc.perform(post("/edit/{id}/active", product.getId())
                        .param("active", "false")
                        .with(asAdmin())
                        .with(csrf()))
                .andExpect(redirectedUrl("/products"));

        assertThat(isActive(product)).isFalse();
    }

    // ---- CSRF ----

    @Test
    void aPostWithoutACsrfTokenIsRefusedEvenForAnAdmin() throws Exception {
        // Another site could make a logged-in admin's browser send this form; without
        // the token from our own page it's refused
        Product product = products.save(product("Test Visible", 10));

        mvc.perform(post("/edit/{id}/active", product.getId())
                        .param("active", "false")
                        .with(asAdmin()))
                .andExpect(status().isForbidden());

        assertThat(isActive(product)).isTrue();
    }

    @Test
    void aPostWithAWrongCsrfTokenIsRefused() throws Exception {
        Product product = products.save(product("Test Visible", 10));

        mvc.perform(post("/edit/{id}/active", product.getId())
                        .param("active", "false")
                        .with(asAdmin())
                        .with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());

        assertThat(isActive(product)).isTrue();
    }
}
