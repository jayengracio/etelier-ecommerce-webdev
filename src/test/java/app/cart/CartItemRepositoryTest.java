package app.cart;

import static app.TestData.product;
import static app.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;

import app.TestContainerConfiguration;
import app.product.Product;
import app.user.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(TestContainerConfiguration.class)
class CartItemRepositoryTest {

    @Autowired
    CartItemRepository cartItems;

    @Autowired
    TestEntityManager em;

    @Test
    void deleteRemovesOnlyThePaidProductsFromThatUsersCart() {
        User buyer = em.persist(user("test-buyer"));
        User someoneElse = em.persist(user("test-someone-else"));
        Product paidA = em.persist(product("Test Paid A", 10));
        Product paidB = em.persist(product("Test Paid B", 10));
        Product addedLater = em.persist(product("Test Added Later", 10));
        em.persist(new CartItem(buyer, paidA, 1));
        em.persist(new CartItem(buyer, paidB, 2));
        em.persist(new CartItem(buyer, addedLater, 1));
        em.persist(new CartItem(someoneElse, paidA, 1));

        cartItems.deleteByUserIdAndProductIdIn(buyer.getId(), List.of(paidA.getId(), paidB.getId()));
        em.flush();
        em.clear();

        assertThat(cartItems.findByUserUsernameOrderById("test-buyer"))
                .extracting(item -> item.getProduct().getId())
                .containsExactly(addedLater.getId());
        assertThat(cartItems.findByUserUsernameOrderById("test-someone-else")).hasSize(1);
    }

    @Test
    void countItemsAddsUpTheQuantitiesInThatUsersCart() {
        User buyer = em.persist(user("test-buyer"));
        User someoneElse = em.persist(user("test-someone-else"));
        Product apple = em.persist(product("Test Apple", 10));
        Product pizza = em.persist(product("Test Pizza", 10));
        em.persist(new CartItem(buyer, apple, 2));
        em.persist(new CartItem(buyer, pizza, 1));
        em.persist(new CartItem(someoneElse, apple, 5));

        // quantities, not lines: 2 apples + 1 pizza
        assertThat(cartItems.countItems("test-buyer")).isEqualTo(3);
    }

    @Test
    void countItemsIsZeroForAnEmptyCart() {
        // SUM over no rows is NULL in SQL; COALESCE turns it into 0
        em.persist(user("test-buyer"));

        assertThat(cartItems.countItems("test-buyer")).isZero();
    }
}
