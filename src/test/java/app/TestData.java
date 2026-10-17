package app;

import app.product.Product;
import app.user.User;
import java.math.BigDecimal;

/**
 * Valid, unsaved entities for tests. Each test changes only what it is about,
 * and gives its rows names starting with "Test" so they never clash with the seed.
 */
public final class TestData {

    private TestData() {}

    public static Product product(String name, int stock) {
        Product product = new Product();
        product.setType("test");
        product.setName(name);
        product.setDetails("Created by a test");
        product.setPrice(new BigDecimal("1.00"));
        product.setStock(stock);
        return product;
    }

    public static User user(String username) {
        return new User(username, "not-a-real-hash");
    }
}
