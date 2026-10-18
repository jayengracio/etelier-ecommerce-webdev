package app.product;

import static app.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.TestContainerConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import(TestContainerConfiguration.class)
class ProductRepositoryTest {

    @Autowired
    ProductRepository products;

    @Autowired
    TestEntityManager em;

    private Product save(String name, int stock, boolean active) {
        Product product = product(name, stock);
        product.setActive(active);
        return em.persistFlushFind(product);
    }

    /**
     * decrementStock and incrementStock are bulk UPDATEs: they change the row
     * but not the Product that Hibernate already holds in memory. Clear it so
     * findById reads the database again.
     */
    private int stockInDatabase(Product product) {
        em.clear();
        return products.findById(product.getId()).orElseThrow().getStock();
    }

    // decrementStock: the webhook takes stock when an order is paid

    @Test
    void decrementStockTakesStockWhenThereIsEnough() {
        Product product = save("Test Widget", 5, true);

        assertThat(products.decrementStock(product.getId(), 3)).isEqualTo(1);
        assertThat(stockInDatabase(product)).isEqualTo(2);
    }

    @Test
    void decrementStockCanTakeTheLastUnit() {
        Product product = save("Test Widget", 5, true);

        assertThat(products.decrementStock(product.getId(), 5)).isEqualTo(1);
        assertThat(stockInDatabase(product)).isZero();
    }

    @Test
    void decrementStockChangesNothingWhenThereIsNotEnough() {
        Product product = save("Test Widget", 5, true);

        assertThat(products.decrementStock(product.getId(), 6)).isZero();
        assertThat(stockInDatabase(product)).isEqualTo(5);
    }

    @Test
    void decrementStockOfAMissingProductUpdatesNothing() {
        assertThat(products.decrementStock(999_999, 1)).isZero();
    }

    // incrementStock: a refund puts stock back

    @Test
    void incrementStockPutsStockBack() {
        Product product = save("Test Widget", 0, true);

        assertThat(products.incrementStock(product.getId(), 4)).isEqualTo(1);
        assertThat(stockInDatabase(product)).isEqualTo(4);
    }

    // Shop and dashboard lists

    @Test
    void shopListShowsOnlyActiveProductsInIdOrder() {
        Product visible = save("Test Visible", 10, true);
        Product hidden = save("Test Hidden", 10, false);

        List<Product> shop = products.findByActiveTrueOrderById();

        assertThat(shop).extracting(Product::isActive).containsOnly(true);
        assertThat(shop)
                .extracting(Product::getId)
                .contains(visible.getId())
                .doesNotContain(hidden.getId())
                .isSorted();
    }

    @Test
    void lowStockListIncludesTheThresholdAndSkipsHiddenProducts() {
        Product atThreshold = save("Test At Five", 5, true);
        Product aboveThreshold = save("Test At Six", 6, true);
        Product hiddenAndLow = save("Test Hidden Low", 1, false);

        List<Product> lowStock = products.findByActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(5);

        assertThat(lowStock)
                .extracting(Product::getId)
                .contains(atThreshold.getId())
                .doesNotContain(aboveThreshold.getId(), hiddenAndLow.getId());
        assertThat(lowStock).allSatisfy(product -> {
            assertThat(product.isActive()).isTrue();
            assertThat(product.getStock()).isLessThanOrEqualTo(5);
        });
        assertThat(lowStock).extracting(Product::getStock).isSorted();
    }

    @Test
    void lowStockTiesAreSortedByName() {
        Product second = save("Test Bbb", 0, true);
        Product first = save("Test Aaa", 0, true);

        List<Integer> ids = products.findByActiveTrueAndStockLessThanEqualOrderByStockAscNameAsc(5).stream()
                .map(Product::getId)
                .toList();

        assertThat(ids.indexOf(first.getId())).isLessThan(ids.indexOf(second.getId()));
    }

    // image: where the picture is, or null for the placeholder

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"/images/Apple.png", "https://example.com/apple.png", "http://example.com/apple.png"})
    void anImageCanBeAPathAUrlOrNothing(String image) {
        Product product = product("Test Widget", 5);
        product.setImage(image);
        Product saved = em.persistFlushFind(product);
        em.clear();

        assertThat(products.findById(saved.getId()).orElseThrow().getImage()).isEqualTo(image);
    }

    // Each of these would be a broken image on some page.
    @ParameterizedTest
    @ValueSource(strings = {"", " ", "images/Apple.png", "Apple.png", "ftp://example.com/apple.png"})
    void anImageMustBeAPathOrAUrl(String image) {
        Product product = product("Test Widget", 5);
        product.setImage(image);

        assertThatThrownBy(() -> products.saveAndFlush(product))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("product_image_check");
    }

    // Catches a typo in a migration that sets a path, like '/images/apple.png'.
    @Test
    void everyPicturePathInTheDatabaseHasAFile() {
        List<String> paths = products.findAll().stream()
                .map(Product::getImage)
                .filter(image -> image != null && image.startsWith("/"))
                .toList();

        assertThat(paths)
                .allSatisfy(path -> assertThat(new ClassPathResource("static" + path).exists())
                        .as(path)
                        .isTrue());
    }
}
