package app.cart;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import app.order.Order;
import app.order.OrderItem;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.product.Product;
import app.product.ProductNotFoundException;
import app.product.ProductRepository;
import app.user.User;
import app.user.UserRepository;
import jakarta.transaction.Transactional;

@Service
public class CartService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;

    public CartService(UserRepository userRepository, ProductRepository productRepository,
            CartItemRepository cartItemRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * Compares the quantity in the cart with the stock (wanted > stock), not just
     * "stack > 0".
     * Without it, you could put 50 apples in the cart when only 10 apples exists in
     * the db.
     */
    @Transactional
    public boolean addToCart(String username, int productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        Optional<CartItem> existing = cartItemRepository.findByUserUsernameAndProductId(username, productId);
        int wanted = existing.map(CartItem::getQuantity).orElse(0) + 1;
        if (wanted > product.getStock()) {
            return false;
        }

        if (existing.isPresent()) {
            existing.get().setQuantity(wanted);
        } else {
            User user = userRepository.findByUsername(username).orElseThrow();
            cartItemRepository.save(new CartItem(user, product, 1));
        }

        return true;
    }

    @Transactional
    public void removeFromCart(String username, int productId) {
        cartItemRepository.findByUserUsernameAndProductId(username, productId).ifPresent(cartItemRepository::delete);
    }

    /**
     * Turn the cart into a PENDING order with today's prices. Takes no stock and
     * keeps the cart:
     * both happen in the Stripe webhook once payment succeeds.
     * Empty if the cart was empty -> throws OutOfStockException if a line already
     * can't be filled,
     * so nobody is sent to pay for something we know is gone.
     */
    @Transactional
    public Optional<Order> createPendingOrder(String username) {
        List<CartItem> cartList = cartItemRepository.findByUserUsernameOrderById(username);
        if (cartList.isEmpty()) {
            return Optional.empty();
        }

        Order order = new Order(userRepository.findByUsername(username).orElseThrow());
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cartList) {
            Product product = item.getProduct();
            if (item.getQuantity() > product.getStock()) {
                throw new OutOfStockException(product.getName());
            }
            order.addItem(new OrderItem(product, item.getQuantity(), product.getPrice()));
            total = total.add(item.getLineTotal());
        }
        order.setTotal(total);

        return Optional.of(orderRepository.save(order));
    }

    /**
     * Payment succeeded: take the stock, mark the order PAID and remove its
     * products from the cart.
     * Does nothing for an unknown order or one that isn't PENDING any more (Stripe
     * can deliver an event twice).
     * Throws OutOfStockException, rolling everything back, if stock ran out after
     * the customer paid.
     */
    @Transactional
    public void completePayment(int orderId, String paymentIntentId) {
        Optional<Order> found = orderRepository.findLockedById(orderId);
        if (found.isEmpty() || found.get().getStatus() != OrderStatus.PENDING) {
            return;
        }
        Order order = found.get();

        // always lock products in the same order,
        // so two payments can't deadlock each other
        List<OrderItem> items = new ArrayList<>(order.getItems());
        items.sort(Comparator.comparingInt(item -> item.getProduct().getId()));

        for (OrderItem item : items) {
            if (productRepository.decrementStock(item.getProduct().getId(), item.getQuantity()) == 0) {
                throw new OutOfStockException(item.getProduct().getName());
            }
        }

        order.markPaid(paymentIntentId);
        List<Integer> productIds = items.stream().map(item -> item.getProduct().getId()).toList();
        cartItemRepository.deleteByUserIdAndProductIdIn(order.getUser().getId(), productIds);
    }

    /**
     * Session expired or payment refunded.
     * Does nothing unless the order is still PENDING
     */
    @Transactional
    public void cancelIfPending(int orderId) {
        orderRepository.findLockedById(orderId)
                .filter(order -> order.getStatus() == OrderStatus.PENDING)
                .ifPresent(Order::cancel);
    }
}
