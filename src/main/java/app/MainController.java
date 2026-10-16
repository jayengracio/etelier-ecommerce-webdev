package app;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import com.stripe.exception.StripeException;

import app.admin.DashboardService;
import app.cart.CartItem;
import app.cart.CartItemRepository;
import app.cart.CartService;
import app.cart.OutOfStockException;
import app.order.Order;
import app.order.OrderRepository;
import app.order.OrderStatus;
import app.payment.PaymentService;
import app.product.NewProductRequest;
import app.product.Product;
import app.product.ProductEditForm;
import app.product.ProductNotFoundException;
import app.product.ProductRepository;
import app.user.User;
import app.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@Controller
public class MainController {

    private final DashboardService dashboardService;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final CartItemRepository cartItemRepository;
    private final CartService cartService;
    private final PasswordEncoder passwordEncoder;
    private final PaymentService paymentService;

    public MainController(ProductRepository productRepository, UserRepository userRepository,
            CartItemRepository cartItemRepository, CartService cartService, PasswordEncoder passwordEncoder,
            OrderRepository orderRepository, PaymentService paymentService, DashboardService dashboardService) {
        this.productRepository = productRepository;
        this.userRepository = userRepository;
        this.cartItemRepository = cartItemRepository;
        this.cartService = cartService;
        this.passwordEncoder = passwordEncoder;
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
        this.dashboardService = dashboardService;
    }

    // ------ PUBLIC PAGES ------
    @GetMapping("/products")
    public String products(Model model, HttpServletRequest request) {
        model.addAttribute("products", productRepository.findAll());
        if (request.isUserInRole("ADMIN")) {
            model.addAttribute("lowStockThreshold", dashboardService.lowStockThreshold());
            return "products_owner.html";
        }
        return "products.html";
    }

    @GetMapping("/details/{id}")
    public String details(@PathVariable int id, Model model) {
        model.addAttribute("product", productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id)));
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

    // ------ USER ACCOUNTS ------
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

        model.addAttribute("orders", orderRepository.findByUserUsernameOrderByCreatedAtDesc(principal.getName()));
        return "profile.html";
    }

    // ------ CART & CHECKOUT (logged-in users) ------
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
        Optional<Order> order;
        try {
            order = cartService.createPendingOrder(principal.getName());
        } catch (OutOfStockException e) {
            return "redirect:/cart?outOfStock";
        }

        if (order.isEmpty()) {
            return "redirect:/cart";
        }

        try {
            return "redirect:" + paymentService.startCheckout(order.get());
        } catch (StripeException e) {
            return "redirect:/checkout?paymentError";
        }
    }

    @GetMapping("/purchase")
    public String purchaseComplete() {
        return "purchase.html";
    }

    /**
     * ResponseStatusException gives a proper 404 (no such order) or
     * 409 (order is in the wrong state), instead of a generic 500
     */
    @PostMapping("/orders/{id}/ship")
    public String shipOrder(@PathVariable int id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        try {
            order.markShipped();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }

        orderRepository.save(order);
        return "redirect:/orders/" + id;
    }

    @PostMapping("/orders/{id}/refund")
    public String refundOrder(@PathVariable int id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.isRefundable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + id + " is " + order.getStatus());
        }

        try {
            paymentService.refund(order);
        } catch (StripeException e) {
            return "redirect:/orders/" + id + "?refundError";
        }
        return "redirect:/orders/" + id + "?refunded";
    }

    // ------ ADMINS (locked to ROLE_ADMIN in SecurityConfig) ------
    @GetMapping("/owners")
    public String owners(Model model) {
        model.addAttribute("stats", dashboardService.stats());
        return "owners.html";
    }

    @PostMapping("/product/add")
    @ResponseBody
    public Product addProduct(@Valid @RequestBody NewProductRequest request) {
        return productRepository.save(request.toProduct());
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable int id, Model model) {
        model.addAttribute("product", productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id)));
        return "edit.html";
    }

    @PostMapping("/edit/product")
    public String editProduct(@RequestParam int id, @Valid @ModelAttribute ProductEditForm form, BindingResult result) {
        Product product = productRepository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));

        if (result.hasErrors()) {
            return "redirect:/edit/" + id + "?invalid";
        }

        product.setDetails(form.details());
        product.setPrice(form.price());
        product.setStock(form.stock());
        productRepository.save(product);
        return "redirect:/products";
    }

    @GetMapping("/view/orders")
    public String viewOrders(@RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Model model) {
        Page<Order> orders = status == null
                ? orderRepository.findAllBy(pageable)
                : orderRepository.findByStatus(status, pageable);

        model.addAttribute("orders", orders);
        model.addAttribute("status", status);
        model.addAttribute("statuses", OrderStatus.values());
        return "view_order.html";
    }

    @GetMapping("/orders/{id}")
    public String orders(@PathVariable int id, Model model) {
        model.addAttribute("order", orderRepository.findWithItemsById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "orders.html";
    }
}
