package app;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = "cart")
    Optional<User> findWithCartByUsername(String username);

    @EntityGraph(attributePaths = "orders")
    Optional<User> findWithOrdersByUsername(String username);

    @EntityGraph(attributePaths = "orders")
    Optional<User> findWithOrdersById(int id);
}
