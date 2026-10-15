package app;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Integer> {

    @EntityGraph(attributePaths = "product")
    List<CartItem> findByUserUsernameOrderById(String username);

    Optional<CartItem> findByUserUsernameAndProductId(String username, int productId);
}
