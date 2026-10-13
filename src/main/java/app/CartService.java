package app;

import java.util.ArrayList;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;

@Service
public class CartService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(UserRepository userRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    /**
     * Return false if the product is out of stock.
     */
    @Transactional
    public boolean addToCart(String username, int productId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();
        if (product.getStock() <= 0) {
            return false;
        }
        user.getCart().add(product);
        product.setStock(product.getStock() - 1);
        return true;
    }

    @Transactional
    public void removeFromCart(String username, int productId) {
        User user = userRepository.findByUsername(username).orElseThrow();
        Product product = productRepository.findById(productId).orElseThrow();
        if (user.getCart().remove(product)) {
            product.setStock(product.getStock() - 1);
        }
    }

    @Transactional
    public void purchase(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.getOrders().addAll(new ArrayList<>(user.getCart()));
        user.getCart().clear();
    }
}
