package app;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;

@Controller
public class MainController {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;
    private final PasswordEncoder passwordEncoder;

    public MainController(ProductRepository productRepository, UserRepository userRepository,
            CartItemRepository cartItemRepository, CartService cartService, PasswordEncoder passwordEncoder) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartService = cartService;
        this.passwordEncoder = passwordEncoder;
    }

    // -- Public Pages Start --
    @GetMapping("/products")
    public String products(Model model, HttpServletRequest request) {
        model.addAttribute("products", productRepository.findAll());
        return request.isUserInRole("ADMIN") ? "products_owner.html" : "products.html";
    }

    @GetMapping("/details/{id}")
    public String details(@PathVariable int id, Model model) {
        model.addAttribute("product", productRepository.findById(id).orElseThrow());
        return "details.html";
    }

    @GetMapping("/stock")
    public String stock() {
        return "stock.html";
    }

    @RequestMapping("/forbid")
    public String forbid() {
        return "forbid.html";
    }

    // -- Accounts --
    @GetMapping("/login")
    public String login(Principal principal) {
        return principal != null ? "redirect:/profile" : "login.html";
    }

    @GetMapping("/register")
    public String register() {
        return "register.html";
    }

    @PostMapping("/register")
    public String registerUser(@RequestParam String username, @RequestParam String password) {
        username = username.trim();
        if (username.length() < 3 || password.length() < 8) {
            return "redirect:/register?invalid";
        }
        if (userRepository.existsByUsername(username)) {
            return "redirect:/register?taken";
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);
        return "redirect:/login?registered";
    }

    @GetMapping("/profile")
    public String profile(Model model, Principal principal, HttpServletRequest request) {
        if (request.isUserInRole("ADMIN")) {
            return "redirect:/owners";
        }

        model.addAttribute("user", userRepository.findWithOrdersByUsername(principal.getName()).orElseThrow());
        return "profile.html";
    }

    // -- Cart & Checkout (logged-in users)
    @GetMapping("/cart")
    public String cart(Model model, Principal principal) {
        List<CartItem> items = cartItemRepository.findByUserUsernameOrderById(principal.getName());
        if (items.isEmpty()) {
            return "empty.html";
        }
        model.addAttribute("items", items);
        return "cart.html";
    }

    @PostMapping("/cart/add/{id}")
    public String addToCart(@PathVariable int id, Principal principal) {
        return cartService.addToCart(principal.getName(), id) ? "redirect:/products" : "redirect:/stock";
    }

    @PostMapping("/cart/remove")
    @ResponseBody
    public Integer removeFromCart(@RequestParam int id, Principal principal) {
        cartService.removeFromCart(principal.getName(), id);
        return id;
    }

    @GetMapping("/checkout")
    public String checkout(Model model, Principal principal) {
        List<CartItem> items = cartItemRepository.findByUserUsernameOrderById(principal.getName());
        if (items.isEmpty()) {
            return "redirect:/cart";
        }
        model.addAttribute("items", items);
        model.addAttribute("total",
                items.stream().map(CartItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        return "checkout.html";
    }

    @PostMapping("/purchase")
    public String purchase(Principal principal) {
        try {
            return cartService.purchase(principal.getName()).isPresent() ? "redirect:/purchase" : "redirect:/cart";
        } catch (OutOfStockException e) {
            return "redirect:/cart?outOfStock";
        }
    }

    @GetMapping("/purchase")
    public String purchaseComplete() {
        return "purchase.html";
    }

    // -- Admin (locked to ROLE_ADMIN in SecurityConfig)
    @GetMapping("/owners")
    public String owners() {
        return "owners.html";
    }

    @PostMapping("/product/add")
    @ResponseBody
    public Product addProduct(@RequestBody Product product) {
        return productRepository.save(product);
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable int id, Model model) {
        model.addAttribute("product", productRepository.findById(id).orElseThrow());
        return "edit.html";
    }

    @PostMapping("/edit/product")
    public String editProduct(@RequestParam int id, @RequestParam String details,
            @RequestParam BigDecimal price, @RequestParam int stock) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setDetails(details);
        product.setPrice(price);
        product.setStock(stock);
        productRepository.save(product);
        return "redirect:/products";
    }

    @GetMapping("/view/orders")
    public String viewOrders(Model model) {
        model.addAttribute("users", userRepository.findAll());
        return "view_order.html";
    }

    @GetMapping("/orders/{id}")
    public String orders(@PathVariable int id, Model model) {
        model.addAttribute("user", userRepository.findWithOrdersById(id).orElseThrow());
        return "orders.html";
    }
}
