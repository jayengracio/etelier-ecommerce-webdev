package app.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

public class ProductValidationTest {

    private static final BigDecimal PRICE = new BigDecimal("9.99");

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private static Set<String> violatedFields(Object bean) {
        return validator.validate(bean).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    // NewProductRequest (POST /product/add)
    @Test
    void validNewProductRequestPasses() {
        NewProductRequest request = new NewProductRequest("food", "Apple", PRICE, "A crisp red apple", 10, null);
        assertThat(violatedFields(request)).isEmpty();
    }

    static Stream<Arguments> invalidNewProductRequests() {
        return Stream.of(
                arguments("blank type", new NewProductRequest(" ", "Apple", PRICE, "Details", 10, null), "type"),
                arguments(
                        "type over 50",
                        new NewProductRequest("x".repeat(51), "Apple", PRICE, "Details", 10, null),
                        "type"),
                arguments("blank name", new NewProductRequest("food", "", PRICE, "Details", 10, null), "name"),
                arguments(
                        "name over 255",
                        new NewProductRequest("food", "x".repeat(256), PRICE, "Details", 10, null),
                        "name"),
                arguments("null price", new NewProductRequest("food", "Apple", null, "Details", 10, null), "price"),
                arguments(
                        "negative price",
                        new NewProductRequest("food", "Apple", new BigDecimal("-0.01"), "Details", 10, null),
                        "price"),
                arguments(
                        "three decimals",
                        new NewProductRequest("food", "Apple", new BigDecimal("1.001"), "Details", 10, null),
                        "price"),
                arguments(
                        "price over 8 digits",
                        new NewProductRequest("food", "Apple", new BigDecimal("123456789.00"), "Details", 10, null),
                        "price"),
                arguments("null details", new NewProductRequest("food", "Apple", PRICE, null, 10, null), "details"),
                arguments("blank details", new NewProductRequest("food", "Apple", PRICE, "  ", 10, null), "details"),
                arguments(
                        "details over 1000",
                        new NewProductRequest("food", "Apple", PRICE, "x".repeat(1001), 10, null),
                        "details"),
                arguments("null stock", new NewProductRequest("food", "Apple", PRICE, "Details", null, null), "stock"),
                arguments(
                        "negative stock", new NewProductRequest("food", "Apple", PRICE, "Details", -1, null), "stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNewProductRequests")
    void invalidNewProductRequests(String description, NewProductRequest request, String field) {
        assertThat(violatedFields(request)).containsExactly(field);
    }

    @Test
    void toProductTrimsTypeAndNameHasNoId() {
        NewProductRequest request = new NewProductRequest("   food", " Apple   ", PRICE, "A crisp red apple", 10, null);

        Product product = request.toProduct();

        assertThat(product.getType()).isEqualTo("food");
        assertThat(product.getName()).isEqualTo("Apple");
        assertThat(product.getId()).isZero();
        assertThat(product.isActive()).isTrue();
    }

    // ProductEditForm (POST /edit/product)
    @Test
    void validEditFormPasses() {
        assertThat(violatedFields(new ProductEditForm("Details", PRICE, 0, null)))
                .isEmpty();
    }

    static Stream<Arguments> invalidEditForms() {
        return Stream.of(
                arguments("blank details", new ProductEditForm(" ", PRICE, 10, null), "details"),
                arguments("null price", new ProductEditForm("Details", null, 10, null), "price"),
                arguments("negative price", new ProductEditForm("Details", new BigDecimal("-1.00"), 10, null), "price"),
                arguments("null stock", new ProductEditForm("Details", PRICE, null, null), "stock"),
                arguments("negative stock", new ProductEditForm("Details", PRICE, -1, null), "stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEditForms")
    void invalidEditFormIsRejected(String description, ProductEditForm form, String field) {
        assertThat(violatedFields(form)).containsExactly(field);
    }

    // image (both forms): optional, and the same rule as the database's check

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/images/Apple.png", "/images/After Hours.png", "https://example.com/a.png"})
    void aPathOrUrlImagePasses(String image) {
        assertThat(violatedFields(new NewProductRequest("food", "Apple", PRICE, "Details", 10, image)))
                .isEmpty();
        assertThat(violatedFields(new ProductEditForm("Details", PRICE, 10, image)))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"images/Apple.png", "Apple.png", "ftp://example.com/a.png", "javascript:alert(1)"})
    void anImageThatIsNotAPathOrUrlIsRejected(String image) {
        assertThat(violatedFields(new NewProductRequest("food", "Apple", PRICE, "Details", 10, image)))
                .containsExactly("image");
        assertThat(violatedFields(new ProductEditForm("Details", PRICE, 10, image)))
                .containsExactly("image");
    }

    @Test
    void anImageOver1000CharactersIsRejected() {
        String image = "/" + "x".repeat(1000);

        assertThat(violatedFields(new ProductEditForm("Details", PRICE, 10, image)))
                .containsExactly("image");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void aBlankImageMeansNoPicture(String image) {
        assertThat(new NewProductRequest("food", "Apple", PRICE, "Details", 10, image).image())
                .isNull();
        assertThat(new ProductEditForm("Details", PRICE, 10, image).image()).isNull();
    }

    @Test
    void anImageIsTrimmed() {
        assertThat(new ProductEditForm("Details", PRICE, 10, "  /images/Apple.png ").image())
                .isEqualTo("/images/Apple.png");
    }
}
