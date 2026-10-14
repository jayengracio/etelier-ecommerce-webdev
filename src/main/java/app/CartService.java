package app;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

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
     * Compares the quantity in the cart with the stock (wanted > stock), not just stack > 0.
     * Without it, you could put 50 apples in the cart when only 10 exists.
     */
    @Transactional
    public boolean addToCart(String username, int productId) {
        Product product = productRepository.findById(productId).orElseThrow();
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
     * Turn the cart into an order and take the stock
     * Empty if the cart was empty, throws OutOfStockException (which rolls
     * everything back) if any line can't be filled.
     */
    @Transactional
    public Optional<Order> purchase(String username) {
        List<CartItem> cartList = cartItemRepository.findByUserUsernameOrderById(username);
        if (cartList.isEmpty()) {
            return Optional.empty();
        }

        // always lock products in the same order, so two purchases can't deadlock each
        // other
        cartList.sort(Comparator.comparingInt(item -> item.getProduct().getId()));

        Order order = new Order(userRepository.findByUsername(username).orElseThrow());
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cartList) {
            Product product = item.getProduct();
            if (productRepository.decrementStock(product.getId(), item.getQuantity()) == 0) {
                throw new OutOfStockException(product.getName());
            }
            order.addItem(new OrderItem(product, item.getQuantity(), product.getPrice()));
            total = total.add(item.getLineTotal());
        }
        order.setTotal(total);

        // No payment step yet, so a purchase count as paid.
        order.setStatus(OrderStatus.PAID);
        orderRepository.save(order);
        cartItemRepository.deleteAll(cartList);
        return Optional.of(order);
    }
}
