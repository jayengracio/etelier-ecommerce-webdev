package app.admin;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.order.Order;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.order.SalesTotals;
import app.order.StatusCount;
import app.product.Product;
import app.product.ProductRepository;

@Service
public class DashboardService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final int lowStockThreshold;

    // Money the shop kept. REFUNDED will stay out of this set.
    private static final Set<OrderStatus> REVENUE_STATUSES = EnumSet.of(OrderStatus.PAID, OrderStatus.SHIPPED);

    public DashboardService(OrderRepository orderRepository, ProductRepository productRepository,
            @Value("${app.low-stock-threshold}") int lowStockThreshold) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.lowStockThreshold = lowStockThreshold;
    }

    public int lowStockThreshold() {
        return lowStockThreshold;
    }

    public record Stats(SalesTotals allTime, SalesTotals lastSevenDays,
            Map<OrderStatus, Long> ordersByStatus, List<Order> recentOrders, List<Product> lowStock,
            int lowStockThreshold) {
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        // Start every status at 0: GROUP BY only returns statuses that have orders
        Map<OrderStatus, Long> byStatus = new EnumMap<>(OrderStatus.class);
        for (OrderStatus status : OrderStatus.values()) {
            byStatus.put(status, 0L);
        }
        for (StatusCount row : orderRepository.countByStatus()) {
            byStatus.put(row.status(), row.count());
        }

        return new Stats(
                orderRepository.salesSince(REVENUE_STATUSES, Instant.EPOCH),
                orderRepository.salesSince(REVENUE_STATUSES, Instant.now().minus(7, ChronoUnit.DAYS)),
                byStatus,
                orderRepository.findTop10ByOrderByCreatedAtDesc(),
                productRepository.findByStockLessThanEqualOrderByStockAscNameAsc(lowStockThreshold),
                lowStockThreshold);
    }
}
