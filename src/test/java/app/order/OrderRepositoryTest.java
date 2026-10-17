package app.order;

import static app.order.OrderStatus.PAID;
import static app.order.OrderStatus.PENDING;
import static app.order.OrderStatus.REFUNDED;
import static app.order.OrderStatus.SHIPPED;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import app.TestContainerConfiguration;
import app.TestData;
import app.user.User;

@DataJpaTest
@Import(TestContainerConfiguration.class)
class OrderRepositoryTest {

    // Postgres stores microseconds; Java can have more digits. Truncate so a
    // value read back equals the one written.
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

    @Autowired
    OrderRepository orders;

    @Autowired
    TestEntityManager em;

    private User customer;

    @BeforeEach
    void setUp() {
        // Start from no orders, whatever the seed holds. @DataJpaTest rolls this back after each test.
        orders.deleteAllInBatch();
        customer = em.persist(TestData.user("test-customer"));
    }

    private Order save(OrderStatus status, String total, Instant createdAt) {
        Order order = new Order(customer);
        order.setStatus(status);
        order.setTotal(new BigDecimal(total));
        order.setCreatedAt(createdAt);
        return em.persist(order);
    }

    @Test
    void countByStatusReturnsOneRowPerStatusInUse() {
        save(PAID, "10.00", NOW);
        save(PAID, "20.00", NOW);
        save(PENDING, "5.00", NOW);
        save(REFUNDED, "7.50", NOW);

        assertThat(orders.countByStatus()).containsExactlyInAnyOrder(
                new StatusCount(PAID, 2),
                new StatusCount(PENDING, 1),
                new StatusCount(REFUNDED, 1));
    }

    @Test
    void salesSinceCountsOnlyTheGivenStatusesInsideTheWindow() {
        Instant weekAgo = NOW.minus(7, DAYS);
        save(PAID, "10.00", NOW);                    // counted
        save(SHIPPED, "25.50", NOW.minus(3, DAYS));  // counted
        save(PAID, "1.00", weekAgo);                 // counted: the window includes its start
        save(PAID, "99.00", NOW.minus(10, DAYS));    // too old
        save(REFUNDED, "40.00", NOW);                // not revenue
        save(PENDING, "15.00", NOW);                 // never paid

        SalesTotals totals = orders.salesSince(List.of(PAID, SHIPPED), weekAgo);

        assertThat(totals.orders()).isEqualTo(3);
        assertThat(totals.revenue()).isEqualByComparingTo("36.50");
    }

    @Test
    void salesSinceWithNoMatchingOrdersIsZeroNotNull() {
        save(PENDING, "15.00", NOW);

        SalesTotals totals = orders.salesSince(List.of(PAID, SHIPPED), NOW.minus(7, DAYS));

        assertThat(totals.orders()).isZero();
        assertThat(totals.revenue()).isEqualByComparingTo("0");
    }

    @Test
    void findByStatusPagesNewestFirstAndLoadsTheCustomer() {
        save(PAID, "1.00", NOW.minus(3, HOURS));
        Order newest = save(PAID, "2.00", NOW.minus(1, HOURS));
        Order middle = save(PAID, "3.00", NOW.minus(2, HOURS));
        save(PENDING, "4.00", NOW);
        // Empty Hibernate's memory, so the query has to load everything itself
        em.flush();
        em.clear();

        Page<Order> first = orders.findByStatus(PAID, PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getTotalPages()).isEqualTo(2);
        assertThat(first.getContent()).extracting(Order::getId).containsExactly(newest.getId(), middle.getId());
        // @EntityGraph("user"): the customer came with the order, so the template
        // can show the username after the transaction ends (open-in-view=false)
        assertThat(Hibernate.isInitialized(first.getContent().get(0).getUser())).isTrue();

        Page<Order> second = orders.findByStatus(PAID, first.nextPageable());
        assertThat(second.getContent()).hasSize(1);
    }

    @Test
    void latestOrdersAreTheTenNewest() {
        for (int i = 0; i < 12; i++) {
            save(PENDING, "1.00", NOW.minus(i, MINUTES));
        }

        List<Order> latest = orders.findTop10ByOrderByCreatedAtDesc();

        assertThat(latest).hasSize(10);
        assertThat(latest).extracting(Order::getCreatedAt)
                .isSortedAccordingTo(Comparator.reverseOrder())
                .startsWith(NOW);
    }
}
