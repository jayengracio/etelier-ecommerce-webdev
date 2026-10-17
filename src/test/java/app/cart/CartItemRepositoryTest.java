package app.cart;

import static app.TestData.product;
import static app.TestData.user;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import app.TestContainerConfiguration;
import app.product.Product;
import app.user.User;

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
}
