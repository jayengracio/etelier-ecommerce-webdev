
# Project Tree

```text
scripts
└── dummyjson-to-sql.mjs                writes the product seed migration from DummyJSON

src/main/java/app/
├── MyApplication.java                  Spring Boot entry point
├── MainController.java                 every page and form handler
├── WebConfig.java                      one-year cache for /fonts/**
├── admin/
│   └── DashboardService.java           dashboard numbers, low-stock threshold
├── cart/
│   ├── CartService.java                cart → PENDING order → PAID, CANCELLED or REFUNDED
│   ├── CartItem.java
│   ├── CartItemRepository.java
│   └── OutOfStockException.java
├── order/
│   ├── Order.java                      status changes that guard their own rules
│   ├── OrderItem.java
│   ├── OrderStatus.java
│   ├── OrderRepository.java            entity graphs, row lock, paging, aggregates
│   ├── StatusCount.java                dashboard: orders per status
│   └── SalesTotals.java                dashboard: revenue totals
├── payment/
│   ├── PaymentService.java             Checkout Sessions, webhook events, refunds
│   ├── StripeWebhookController.java    POST /stripe/webhook, checks the signature
│   ├── StripeConfig.java               the StripeClient bean
│   └── StripeProperties.java           stripe.* keys; startup fails if missing
├── product/
│   ├── Product.java
│   ├── ProductRepository.java          stock decrement and increment in SQL, featured, low stock
│   ├── Review.java                     DummyJSON's reviews (seeded, not shown yet)
│   ├── ReviewRepository.java
│   ├── NewProductRequest.java          validated JSON for "add product"
│   ├── ProductEditForm.java            validated edit form
│   └── ProductNotFoundException.java   answers 404
├── security/
│   ├── SecurityConfig.java             URL rules, login/logout, CSRF, BCrypt
│   └── AppUserDetailsService.java      loads users for Spring Security
└── user/
    ├── User.java
    └── UserRepository.java

src/main/resources/
├── application.properties              port, database, JPA, Stripe, low-stock threshold
├── db/migration/                       V1 schema, V2 accounts, V3 DummyJSON products (Flyway)
├── templates/                          Thymeleaf pages, error/404 page
│   └── fragments/                      layout, navbar, footer, icons, cart toast, product card,
│                                       product picture, product filters, status badge
└── static/
    ├── code.js                         cart toast, product filters
    ├── fonts/                          Geist and Inter (WOFF2)
    └── images/                         logo, profile avatar

src/main/frontend/                      the stylesheet's source; built into target/, never committed
├── app.css                             Tailwind entry: fonts, design tokens, base styles, shared components
└── package.json                        exact Tailwind CLI version; Maven runs npm ci and npm run build

src/test/java/app/
├── TestContainerConfiguration.java     throwaway PostgreSQL 17 for the tests
├── TestData.java                       valid unsaved products and users
├── MyApplicationTests.java             the application starts
├── MainControllerTest.java             every page's model, add to cart, Buy, bad input
├── cart/                               CartServiceTest, CartItemRepositoryTest
├── order/                              OrderTest, SalesTotalsTest, OrderRepositoryTest
├── payment/                            PaymentWebTest
├── product/                            ProductValidationTest, ProductRepositoryTest, ReviewRepositoryTest
└── security/                           SecurityConfigTest

src/test/resources/
└── application-test.properties         dummy Stripe keys for the test profile

compose.yaml                            the PostgreSQL container
.git-blame-ignore-revs                  formatting commits git blame skips
```
