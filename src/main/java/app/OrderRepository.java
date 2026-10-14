package app;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    // Profile page: a user's orders with their items and products.
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserUsernameOrderByCreatedAtDesc(String username);

    // Admin list: every order with its customer.
    @EntityGraph(attributePaths = "user")
    List<Order> findAllByOrderByCreatedAtDesc();

    // Admin detail page: one order with everything.
    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    Optional<Order> findWithItemsById(int id);
}
