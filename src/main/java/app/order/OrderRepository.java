package app.order;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface OrderRepository extends JpaRepository<Order, Integer> {

    // Profile page: a user's orders with their items and products.
    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserUsernameOrderByCreatedAtDesc(String username);

    // Admin list: one page of orders with their customer. The pageable carries the sort
    @EntityGraph(attributePaths = "user")
    Page<Order> findAllBy(Pageable pageable);

    // Admin list filtered by status, e.g. everything PAID that still needs shipping
    @EntityGraph(attributePaths = "user")
    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    // Admin detail page: one order with everything.
    @EntityGraph(attributePaths = {"user", "items", "items.product"})
    Optional<Order> findWithItemsById(int id);

    // Webhook: SELECT ... FOR UPDATE, so two deliveries of the same event run one
    // after the other
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Order> findLockedById(int id);

    // Dashboard: one row per status that has at least one order
    @Query("SELECT new app.order.StatusCount(o.status, COUNT(o)) FROM Order o GROUP BY o.status")
    List<StatusCount> countByStatus();

    // Dashboard: order count and money for the given statuses, placed since a
    // moment
    @Query("""
            SELECT new app.order.SalesTotals(COUNT(o), SUM(o.total))
            FROM Order o
            WHERE o.status IN :statuses AND o.createdAt >= :since
            """)
    SalesTotals salesSince(Collection<OrderStatus> statuses, Instant since);

    // Dashboard: latest orders with their customer (the template shows the
    // username)
    @EntityGraph(attributePaths = "user")
    List<Order> findTop10ByOrderByCreatedAtDesc();
}
