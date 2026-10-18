package app;

import static app.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.order.Order;
import app.order.OrderItem;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.product.Product;
import app.product.ProductRepository;
import app.user.User;
import app.user.UserRepository;
import com.stripe.StripeClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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
 * What MainController does with requests it should refuse: ids that don't exist,
 * values that don't parse, forms that don't validate, orders in the wrong state.
 * Each refusal has a valid twin, so a test can't pass only because the request
 * was broken in some other way.
 */
@SpringBootTest
@Import(TestContainerConfiguration.class)
@ActiveProfiles("test")
class MainControllerTest {

    private static final int MISSING_ID = 999_999;

    @MockitoBean
    StripeClient stripe;

    @Autowired
    WebApplicationContext context;

    @Autowired
    ProductRepository products;

    @Autowired
    OrderRepository orders;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    private MockMvc mvc;
    private User customer;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        customer = users.save(TestData.user("test-customer"));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM orders WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'test-%')");
        jdbc.update("DELETE FROM users WHERE username LIKE 'test-%'");
        jdbc.update("DELETE FROM product WHERE type = 'test'");
    }

    // Spring Security's user(), not TestData's: a principal with no database row
    private static RequestPostProcessor asCustomer() {
        return user("test-customer").roles("USER");
    }

    private static RequestPostProcessor asAdmin() {
        return user("owner").roles("ADMIN");
    }

    private Product reload(Product product) {
        return products.findById(product.getId()).orElseThrow();
    }

    private Order orderIn(OrderStatus status, String stripeSessionId) {
        Product product = products.save(product("Test Apple", 10));
        Order order = new Order(customer);
        order.addItem(new OrderItem(product, 1, product.getPrice()));
        order.setTotal(product.getPrice());
        order.setStatus(status);
        order.setStripeSessionId(stripeSessionId);
        return orders.save(order);
    }

    private OrderStatus statusOf(Order order) {
        return orders.findById(order.getId()).orElseThrow().getStatus();
    }

    private int countTestProducts() {
        return jdbc.queryForObject("SELECT count(*) FROM product WHERE type = 'test'", Integer.class);
    }

    // ---- GET /details/{id} ----

    @Test
    void aProductThatDoesNotExistGives404() throws Exception {
        mvc.perform(get("/details/{id}", MISSING_ID)).andExpect(status().isNotFound());
    }

    @Test
    void aVisibleProductIsShownToEveryone() throws Exception {
        Product visible = products.save(product("Test Visible", 10));

        mvc.perform(get("/details/{id}", visible.getId())).andExpect(status().isOk());
    }

    @Test
    void aHiddenProductGives404ToVisitorsAndCustomers() throws Exception {
        // 404, not 403: a hidden product looks the same as one that never existed
        Product product = product("Test Hidden", 10);
        product.setActive(false);
        Product hidden = products.save(product);

        mvc.perform(get("/details/{id}", hidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/details/{id}", hidden.getId()).with(asCustomer())).andExpect(status().isNotFound());
        mvc.perform(get("/details/{id}", hidden.getId()).with(asAdmin())).andExpect(status().isOk());
    }

    // ---- POST /cart/add/{id} and the navbar's cart count ----

    // What code.js sends; the header is what routes it to the JSON handler
    private ResultActions addFromScript(int productId) throws Exception {
        return mvc.perform(post("/cart/add/{id}", productId)
                .header("X-Requested-With", "XMLHttpRequest")
                .with(asCustomer())
                .with(csrf()));
    }

    @Test
    void aScriptAddAnswersTheNewCartCount() throws Exception {
        Product product = products.save(product("Test Apple", 10));

        addFromScript(product.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartCount").value(1));
        addFromScript(product.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartCount").value(2));
    }

    @Test
    void aScriptAddWithNoStockLeftGives409AndTheUnchangedCount() throws Exception {
        Product lastOne = products.save(product("Test Last One", 1));

        addFromScript(lastOne.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cartCount").value(1));
        addFromScript(lastOne.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.cartCount").value(1));
    }

    @Test
    void aScriptAddOfAProductThatDoesNotExistGives404() throws Exception {
        addFromScript(MISSING_ID).andExpect(status().isNotFound());
    }

    @Test
    void aFormAddStillRedirects() throws Exception {
        // No header: the original handler. Exact URLs, so the cartCount model attribute
        // can't have been tacked on as ?cartCount=
        Product lastOne = products.save(product("Test Last One", 1));

        mvc.perform(post("/cart/add/{id}", lastOne.getId()).with(asCustomer()).with(csrf()))
                .andExpect(redirectedUrl("/products"));
        mvc.perform(post("/cart/add/{id}", lastOne.getId()).with(asCustomer()).with(csrf()))
                .andExpect(redirectedUrl("/stock"));
    }

    // Every page a logged-in customer can open. A page served outside MainController
    // (like Spring Boot's own welcome page for "/") would miss cartCount and fail here.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "/",
                "/products",
                "/details/{id}",
                "/stock",
                "/forbid",
                "/register",
                "/cart",
                "/checkout",
                "/profile",
                "/purchase"
            })
    void everyPageGetsTheCartCountForTheNavbar(String url) throws Exception {
        Product apple = products.save(product("Test Apple", 10));
        Product pizza = products.save(product("Test Pizza", 10));
        addFromScript(apple.getId());
        addFromScript(apple.getId());
        addFromScript(pizza.getId());

        mvc.perform(get(url, apple.getId()).with(asCustomer()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("cartCount", 3L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/products"})
    void visitorsGetACartCountOfZero(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(model().attribute("cartCount", 0L));
    }

    // ---- GET /view/orders?status= ----

    @Test
    void anUnknownOrderStatusGives400() throws Exception {
        // The String can't become an OrderStatus, so Spring refuses it before the method runs
        mvc.perform(get("/view/orders").param("status", "nonsense").with(asAdmin()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/view/orders").param("status", "PAID").with(asAdmin())).andExpect(status().isOk());
    }

    // ---- POST /product/add (JSON) ----

    private static String newProductJson(String fields) {
        return """
                {"type": "test", "name": "Test New", "price": 2.50, "details": "Added by a test", "stock": 5%s}
                """.formatted(fields);
    }

    @Test
    void aValidNewProductIsSaved() throws Exception {
        mvc.perform(post("/product/add")
                        .with(asAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProductJson("")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Test New"));

        assertThat(countTestProducts()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                // each one breaks a single rule of NewProductRequest
                "{\"type\": \"test\", \"price\": 2.50, \"details\": \"no name\", \"stock\": 5}",
                "{\"type\": \"test\", \"name\": \" \", \"price\": 2.50, \"details\": \"blank name\", \"stock\": 5}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": -1, \"details\": \"negative\", \"stock\": 5}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": 2.505, \"details\": \"3 decimals\", \"stock\": 5}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": 2.50, \"details\": \"\", \"stock\": 5}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": 2.50, \"details\": \"negative\", \"stock\": -1}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": 2.50, \"details\": \"no stock\"}",
                "{\"type\": \"test\", \"name\": \"Test\", \"price\": \"cheap\", \"details\": \"text\", \"stock\": 5}",
                "{not json"
            })
    void anInvalidNewProductGives400AndSavesNothing(String json) throws Exception {
        mvc.perform(post("/product/add")
                        .with(asAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());

        assertThat(countTestProducts()).isZero();
    }

    @Test
    void anIdInTheJsonDoesNotOverwriteAProduct() throws Exception {
        // NewProductRequest has no id field, so the id is ignored and a new row is made
        Product existing = products.save(product("Test Existing", 10));

        mvc.perform(post("/product/add")
                        .with(asAdmin())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newProductJson(", \"id\": " + existing.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(not(existing.getId())));

        assertThat(reload(existing).getName()).isEqualTo("Test Existing");
        assertThat(countTestProducts()).isEqualTo(2);
    }

    // ---- POST /edit/product (form) ----

    @ParameterizedTest(name = "details=''{0}'' price={1} stock={2}")
    @CsvSource({
        "'',          2.00,  5", // blank details
        "New details, -0.01, 5", // negative price
        "New details, 2.001, 5", // more than 2 decimals
        "New details, 2.00,  -1", // negative stock
        "New details, 2.00,  many", // not a number: a binding error, also in BindingResult
        "New details, ,      5" // no price
    })
    void anInvalidEditRedirectsBackAndChangesNothing(String details, String price, String stock) throws Exception {
        Product product = products.save(product("Test Edit Me", 10));

        mvc.perform(post("/edit/product")
                        .param("id", String.valueOf(product.getId()))
                        .param("details", details)
                        .param("price", price)
                        .param("stock", stock)
                        .with(asAdmin())
                        .with(csrf()))
                .andExpect(redirectedUrl("/edit/" + product.getId() + "?invalid"));

        Product unchanged = reload(product);
        assertThat(unchanged.getDetails()).isEqualTo("Created by a test");
        assertThat(unchanged.getPrice()).isEqualByComparingTo("1.00");
        assertThat(unchanged.getStock()).isEqualTo(10);
    }

    @Test
    void aValidEditIsSaved() throws Exception {
        Product product = products.save(product("Test Edit Me", 10));

        mvc.perform(post("/edit/product")
                        .param("id", String.valueOf(product.getId()))
                        .param("details", "New details")
                        .param("price", "2.00")
                        .param("stock", "5")
                        .with(asAdmin())
                        .with(csrf()))
                .andExpect(redirectedUrl("/products"));

        Product edited = reload(product);
        assertThat(edited.getDetails()).isEqualTo("New details");
        assertThat(edited.getPrice()).isEqualByComparingTo(new BigDecimal("2.00"));
        assertThat(edited.getStock()).isEqualTo(5);
    }

    @Test
    void editingAProductThatDoesNotExistGives404() throws Exception {
        mvc.perform(post("/edit/product")
                        .param("id", String.valueOf(MISSING_ID))
                        .param("details", "New details")
                        .param("price", "2.00")
                        .param("stock", "5")
                        .with(asAdmin())
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---- POST /orders/{id}/ship and /refund ----

    @ParameterizedTest
    @EnumSource(names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPaidOrderCanBeShipped(OrderStatus status) throws Exception {
        Order order = orderIn(status, "cs_test_1");

        mvc.perform(post("/orders/{id}/ship", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict());

        assertThat(statusOf(order)).isEqualTo(status);
    }

    @Test
    void aPaidOrderIsShipped() throws Exception {
        Order order = orderIn(OrderStatus.PAID, "cs_test_1");

        mvc.perform(post("/orders/{id}/ship", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(redirectedUrl("/orders/" + order.getId()));

        assertThat(statusOf(order)).isEqualTo(OrderStatus.SHIPPED);
    }

    @ParameterizedTest
    @EnumSource(names = "PAID", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPaidOrderCanBeRefunded(OrderStatus status) throws Exception {
        Order order = orderIn(status, "cs_test_1");

        mvc.perform(post("/orders/{id}/refund", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict());

        assertThat(statusOf(order)).isEqualTo(status);
    }

    @Test
    void aPaidOrderWithoutAStripeSessionCannotBeRefunded() throws Exception {
        // Nothing to refund at Stripe; the controller checks before calling it
        Order order = orderIn(OrderStatus.PAID, null);

        mvc.perform(post("/orders/{id}/refund", order.getId()).with(asAdmin()).with(csrf()))
                .andExpect(status().isConflict());

        assertThat(statusOf(order)).isEqualTo(OrderStatus.PAID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/orders/{id}/ship", "/orders/{id}/refund"})
    void shippingOrRefundingAnOrderThatDoesNotExistGives404(String url) throws Exception {
        mvc.perform(post(url, MISSING_ID).with(asAdmin()).with(csrf())).andExpect(status().isNotFound());
    }
}
