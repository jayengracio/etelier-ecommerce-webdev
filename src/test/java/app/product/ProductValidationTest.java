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
        NewProductRequest request = new NewProductRequest("food", "Apple", PRICE, "A crisp red apple", 10);
        assertThat(violatedFields(request)).isEmpty();
    }

    static Stream<Arguments> invalidNewProductRequests() {
        return Stream.of(
                arguments("blank type", new NewProductRequest(" ", "Apple", PRICE, "Details", 10), "type"),
                arguments("type over 50", new NewProductRequest("x".repeat(51), "Apple", PRICE, "Details", 10), "type"),
                arguments("blank name", new NewProductRequest("food", "", PRICE, "Details", 10), "name"),
                arguments(
                        "name over 255", new NewProductRequest("food", "x".repeat(256), PRICE, "Details", 10), "name"),
                arguments("null price", new NewProductRequest("food", "Apple", null, "Details", 10), "price"),
                arguments(
                        "negative price",
                        new NewProductRequest("food", "Apple", new BigDecimal("-0.01"), "Details", 10),
                        "price"),
                arguments(
                        "three decimals",
                        new NewProductRequest("food", "Apple", new BigDecimal("1.001"), "Details", 10),
                        "price"),
                arguments(
                        "price over 8 digits",
                        new NewProductRequest("food", "Apple", new BigDecimal("123456789.00"), "Details", 10),
                        "price"),
                arguments("null details", new NewProductRequest("food", "Apple", PRICE, null, 10), "details"),
                arguments("blank details", new NewProductRequest("food", "Apple", PRICE, "  ", 10), "details"),
                arguments(
                        "details over 1000",
                        new NewProductRequest("food", "Apple", PRICE, "x".repeat(1001), 10),
                        "details"),
                arguments("null stock", new NewProductRequest("food", "Apple", PRICE, "Details", null), "stock"),
                arguments("negative stock", new NewProductRequest("food", "Apple", PRICE, "Details", -1), "stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidNewProductRequests")
    void invalidNewProductRequests(String description, NewProductRequest request, String field) {
        assertThat(violatedFields(request)).containsExactly(field);
    }

    @Test
    void toProductTrimsTypeAndNameHasNoId() {
        NewProductRequest request = new NewProductRequest("   food", " Apple   ", PRICE, "A crisp red apple", 10);

        Product product = request.toProduct();

        assertThat(product.getType()).isEqualTo("food");
        assertThat(product.getName()).isEqualTo("Apple");
        assertThat(product.getId()).isZero();
        assertThat(product.isActive()).isTrue();
    }

    // ProductEditForm (POST /edit/product)
    @Test
    void validEditFormPasses() {
        assertThat(violatedFields(new ProductEditForm("Details", PRICE, 0))).isEmpty();
    }

    static Stream<Arguments> invalidEditForms() {
        return Stream.of(
                arguments("blank details", new ProductEditForm(" ", PRICE, 10), "details"),
                arguments("null price", new ProductEditForm("Details", null, 10), "price"),
                arguments("negative price", new ProductEditForm("Details", new BigDecimal("-1.00"), 10), "price"),
                arguments("null stock", new ProductEditForm("Details", PRICE, null), "stock"),
                arguments("negative stock", new ProductEditForm("Details", PRICE, -1), "stock"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidEditForms")
    void invalidEditFormIsRejected(String description, ProductEditForm form, String field) {
        assertThat(violatedFields(form)).containsExactly(field);
    }
}
