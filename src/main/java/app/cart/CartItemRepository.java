package app.cart;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    @EntityGraph(attributePaths = "product")
    List<CartItem> findByUserUsernameOrderById(String username);

    Optional<CartItem> findByUserUsernameAndProductId(String username, int productId);

    // The navbar badge: how many things are in the cart (2 apples + 1 pizza = 3), 0 if it's empty
    @Query("SELECT COALESCE(SUM(c.quantity), 0) FROM CartItem c WHERE c.user.username = :username")
    long countItems(String username);

    // Webhook: remove the products that were just paid for.
    // Anything added since stays in the cart
    void deleteByUserIdAndProductIdIn(int userId, Collection<Integer> productIds);
}
