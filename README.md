# Web Dev E-commerce Shop

A small online shop built with Spring Boot and Thymeleaf. Customers can browse products, keep a cart, pay through Stripe and see their order history. An admin gets a sales dashboard with low-stock alerts, can filter and page through every order, ship or refund paid orders, and add, edit or hide products.

The project started as a web development group project for University ([report](docs/report.pdf), [wireframe design](docs/wireframe_design.pdf)). I decided to see if I could update, further refine and implement features as practice. The framework was upgraded, the security, database and order model were rebuilt, and real payments were added. The [Then vs now](#then-vs-now) section explains each change.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 25 (Eclipse Temurin) |
| Framework | Spring Boot 4.1.1 (Spring MVC) |
| Views | Thymeleaf server-side templates, plain CSS and JavaScript |
| Security | Spring Security: form login, BCrypt password hashing, `USER`/`ADMIN` roles, CSRF protection |
| Persistence | Spring Data JPA (Hibernate) |
| Validation | Bean Validation (Hibernate Validator) on request records |
| Database | PostgreSQL 17, run in Docker |
| Schema migrations | Flyway (versioned SQL scripts) |
| Build | Maven 3.9 |
| Monitoring | Spring Boot Actuator (included, not configured yet) |
| Payments | Stripe Checkout (hosted payment page) and a signed webhook, via `stripe-java` |

## Running it locally

### Prerequisites

- JDK 25
- Maven 3.9+
- Docker Desktop (or another Docker engine with Compose)
- A Stripe account in test mode (a sandbox) and the [Stripe CLI](https://docs.stripe.com/stripe-cli)

### One-time Stripe setup

Copy the secret key from the Stripe dashboard (test mode: **Developers → API keys**), then in PowerShell:

```powershell
$sk = "sk_test_..."
[Environment]::SetEnvironmentVariable("STRIPE_SECRET_KEY", $sk, "User")
# The webhook secret must come from the same account as the key, hence --api-key
$whsec = stripe listen --api-key $sk --print-secret
[Environment]::SetEnvironmentVariable("STRIPE_WEBHOOK_SECRET", $whsec, "User")
```

Restart your editor and terminals so they pick up both variables. The app refuses to start without them.

### Start

```powershell
# 1. Start PostgreSQL in the background (defined in compose.yaml)
docker compose up -d

# 2. In a second terminal, keep this running: it forwards Stripe's events to the app
stripe listen --api-key $env:STRIPE_SECRET_KEY --events checkout.session.completed,checkout.session.expired --forward-to localhost:3000/stripe/webhook

# 3. In a third terminal, start the app. Flyway creates and seeds the schema on first run.
mvn spring-boot:run
```

Then open **<http://localhost:3000>**. Pay with the test card `4242 4242 4242 4242`, any future expiry date and any CVC.

### Seed accounts

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `password` | ADMIN |
| `Phil` | `1234pass` | USER |
| `Joseph` | `w0w` | USER |

### Stop and reset the database

```bash
# Stop the app: Ctrl+C in the terminal running mvn

docker compose down       # stop the database, keep its data
docker compose down -v    # stop and wipe the data; Flyway rebuilds it from V1 next start
```

### Look inside the database

```bash
docker compose exec db psql -U shop -d shop
```

Useful commands inside psql: `\dt` (list tables), `\d orders` (describe a table), `SELECT * FROM flyway_schema_history;` (applied migrations), `\q` (quit).

### Build a runnable jar

```bash
mvn clean package
java -jar target/johne-0.0.1.jar
```

### Configuration

| Setting | Default | Notes |
| --- | --- | --- |
| HTTP port | `3000` | `server.port` in `application.properties` |
| Database | `localhost:5432`, db/user `shop` | matches `compose.yaml` |
| `DB_PASSWORD` env var | `devpassword` | local-only default; set the variable to override it |
| `APP_BASE_URL` env var | `http://localhost:3000` | where Stripe sends the customer back after paying or cancelling |

## Features

- **Catalogue:** product list and product detail pages, open to everyone. Products an admin has hidden don't appear, and their pages return 404.
- **Accounts:** registration (username of 3+ characters, password of 8+, unique usernames), login and logout.
- **Cart:** one row per product with a quantity. Shows "Only N left" and "No longer sold" warnings, and blocks adding more than is in stock.
- **Checkout and payment:** "Pay with Stripe" creates a PENDING order with each item's current price and sends the customer to Stripe's hosted payment page. When Stripe's webhook confirms the payment, the stock is taken, the order becomes PAID and those products leave the cart. If the customer never pays, the order is CANCELLED when the session expires. If stock ran out while they were paying, the payment is refunded automatically.
- **Order history:** customers see their past orders with dates, items and status (PENDING, PAID, SHIPPED, CANCELLED, REFUNDED) on their profile.
- **Admin dashboard** (`/owners`): revenue all time and for the last 7 days, order counts by status (each links to the filtered list), low-stock products, and the 10 most recent orders.
- **Order management:** every order, 20 per page, filterable by status. An order's page links to its payment in the Stripe Dashboard. PAID orders can be marked as shipped, or refunded: the money goes back through Stripe and the stock returns to the shelf.
- **Product management:** add and edit products, with server-side validation. Products can be hidden from the shop and shown again; they're never deleted, so old orders keep showing what was bought.

## Then vs now

The original version worked as a demo but had problems that would break a real shop. Each row below names the old behaviour, what replaced it, and why.

### Platform

| Before | Now | Why |
| --- | --- | --- |
| Spring Boot 2.2.4, Java 13 | Spring Boot 4.1.1, Java 25 | Boot 2.x stopped getting fixes years ago. Moving up also meant switching `javax.*` imports to `jakarta.*`. |
| H2 in-memory database, `ddl-auto=create`, `data.sql` | PostgreSQL in Docker, Flyway migrations, `ddl-auto=validate` | H2 data disappeared on every restart. Now the schema lives in versioned SQL files, and the app refuses to start if the Java entities and the tables disagree. |
| `@Autowired` fields | Constructor injection | Dependencies are explicit and `final`, and the class is easier to test. |
| Every class in one flat package | Feature packages: `admin`, `cart`, `order`, `payment`, `product`, `security`, `user` | Everything about one feature sits together. |


### Security

| Before | Now | Why |
| --- | --- | --- |
| The logged-in user was kept in fields on `MainController` (`loggedUser`, `loggedIn`) | Spring Security keeps a session per browser, and the controller reads the user from `Principal` | A controller is a single shared object. When one person logged in, **every visitor** was logged in as them. The admin edit form had the same bug through a shared `tempProduct` field. |
| Plain-text passwords, checked by loading every user and comparing strings | BCrypt hashes, checked by Spring Security | Stored passwords can't be read back. Migration V3 hashed the existing passwords in the database using `pgcrypto`. |
| `owner` boolean checked inside some handlers; `/owners`, `/product/add` and `/view/orders` had no check | `role` column (`USER`/`ADMIN`); admin URLs locked in `SecurityConfig` | Access rules live in one place and can't be skipped by forgetting an `if`. |
| A "Guest" row in the users table (id 1) stood in for logged-out visitors | Removed (V4); logged out simply means no session | A fake account that every anonymous visitor shared. |
| State changes over `GET` (`/purchase`, `/cart/remove`, add to cart); no CSRF protection | `POST` with a CSRF token (AJAX calls send it in a header) | A link or image on another site could empty a cart or place an order on the user's behalf. |
| No registration rules; duplicate usernames possible | Length rules, `UNIQUE` username in the database, friendly error messages | |

### Cart, orders and money

| Before | Now | Why |
| --- | --- | --- |
| `Double` prices | `BigDecimal` in Java, `NUMERIC(10,2)` in the database (V5) | Floating point can't represent values like 0.10 exactly, so totals drift. |
| Cart = a many-to-many list of products; the same product repeated for each copy | `cart_items` table with a `quantity`, one row per user and product | Quantities are real numbers, and adding the same item twice doesn't create duplicate rows. |
| Orders = one flat list of products per user, with no date, status or price | `orders` + `order_items` (V6), each with `created_at`, `status` and the `unit_price` at purchase time | Purchases are grouped, and a later price change doesn't rewrite order history. |
| Stock went down when an item was **added to the cart** (read, subtract, save) | Stock is **checked** at add-to-cart and **taken** when Stripe confirms payment, with one conditional `UPDATE ... WHERE stock >= qty` per line inside a transaction | Abandoned carts no longer lock up stock. Two people buying the last item at the same time can't both succeed. |
| Admin "orders" page listed users and their product lists | Admin sees every order, opens one, and ships it (PAID → SHIPPED) | |

### Payment/Checkout

| Before | Now | Why |
| --- | --- | --- |
| "Confirm Purchase" just moved the cart into orders; the billing form was never saved | Stripe Checkout's hosted page takes the payment; the app never sees card details | Real payments without handling card data, which stays with Stripe. |
| An order counted as done as soon as the button was clicked | The order stays PENDING until a **signed webhook** from Stripe confirms payment | A browser landing on the "thank you" page proves nothing, because anyone can open that URL. Only Stripe's signed event can be trusted. |

### Admin

| Before | Now | Why |
| --- | --- | --- |
| The admin page had Log Out, View Orders and a "Change Orders" button that only went to `/` | A dashboard: revenue, orders by status, low-stock products, recent orders | The numbers come from `COUNT`/`SUM ... GROUP BY` queries mapped straight into records, so the page costs four small queries however many orders exist. |
| `/view/orders` loaded every order on every visit | 20 per page (`Pageable`), filterable by `?status=` | One page is a `LIMIT/OFFSET` query plus a count, so the page stays fast as orders grow. |
| No refunds; an admin could only ship | Refund for PAID orders: a Stripe refund, then status REFUNDED and the stock goes back (V9) | Stripe is called first, with an idempotency key, so clicking again after a failure can never refund twice. CANCELLED still means "never paid", so revenue leaves out both. |
| A product couldn't be removed at all | Products can be hidden and shown again (`active` flag, V10) | `order_items` references products, so deleting one would break order history. Hidden products leave the shop, the cart's checkout and the low-stock list. |
| Bad product input (negative price, empty name) hit a database `CHECK` and showed a 500; a price of `1.999` was silently rounded | Bean Validation on request records; the edit form shows a message | The browser's `min`/`step` checks are easy to remove, so the server checks too. |
| "Add product" bound the JSON straight to the `Product` entity | A `NewProductRequest` record with no `id` field | A request containing `"id": 3` overwrote product 3 instead of adding a new one. |
| A missing product (`/details/999`) showed a 500 | A 404 page | `ProductNotFoundException` carries `@ResponseStatus(NOT_FOUND)`. |

## Project tree

```text
src/main/java/app/
  MyApplication.java           Spring Boot entry point
  MainController.java          all page and form handlers
  admin/
    DashboardService.java      dashboard numbers in one read-only transaction; owns the low-stock threshold
  cart/
    CartService.java           cart items; create PENDING orders, complete, cancel or refund them (transactional)
    CartItem.java, CartItemRepository.java, OutOfStockException.java
  order/
    Order.java                 status changes (markPaid, markShipped, cancel, refund) guard their own rules
    OrderItem.java, OrderStatus.java
    OrderRepository.java       entity graphs, row lock, paging, dashboard aggregates
    StatusCount.java,          records the dashboard queries map into
    SalesTotals.java
  payment/
    PaymentService.java        Stripe Checkout Sessions; paid/expired sessions; automatic and admin refunds
    StripeWebhookController.java  POST /stripe/webhook: verifies the signature, dispatches events
    StripeConfig.java,         the StripeClient bean and the stripe.* settings
    StripeProperties.java        (startup fails if the keys are missing)
  product/
    Product.java, ProductRepository.java   entity; stock decrement/increment in SQL
    NewProductRequest.java,    validated request records (add / edit)
    ProductEditForm.java
    ProductNotFoundException.java          answers 404
  security/
    SecurityConfig.java        URL rules, login/logout, BCrypt encoder
    AppUserDetailsService.java loads users from the database for Spring Security
  user/
    User.java, UserRepository.java
src/main/resources/
  application.properties       port, datasource, JPA, Flyway, Stripe and low-stock settings
  db/migration/V*.sql          Flyway migrations (see below)
  templates/                   Thymeleaf pages (+ fragments/navbar.html, error/404.html)
  static/                      style.css, code.js, product images
compose.yaml                   the PostgreSQL container
```

Design notes:

- **Links run from child to parent only** (`CartItem → User`, `OrderItem → Order`). A user's cart and orders are loaded through repositories, not through collections on `User`.
- **`spring.jpa.open-in-view=false`:** the database session closes before the template renders. Each query states what it needs with `@EntityGraph`, so a template can't trigger extra hidden queries.
- **Stock is decremented in SQL** (`ProductRepository.decrementStock`), so the check and the update happen in one atomic step. Payments lock products in id order so two of them can't deadlock.
- **Stripe can deliver the same event twice, even at the same time.** `CartService.completePayment` locks the order row (`SELECT ... FOR UPDATE`) and only acts on a PENDING order, so a repeated event never takes stock twice.
- **No database transaction stays open during a Stripe call.** The order is saved first, then the Checkout Session is created, then its id is saved. Refunds run after the stock transaction has rolled back.
- **Amounts go to Stripe in cents** with `movePointRight(2).longValueExact()`, with no floating-point step in between.
- **Admin refunds call Stripe first, then the database.** If the database step fails, the order still says PAID, and clicking Refund again is safe: the same idempotency key makes Stripe return the existing refund. The other order would risk an order marked REFUNDED whose customer never got their money.
- **Paging only fetches to-one relations.** The order list's `@EntityGraph` loads `user` but not `items`. Fetching a collection would make Hibernate page in memory, which loads every row again.
- **Admin endpoints live under paths `SecurityConfig` already locks** (for example `POST /edit/{id}/active`). A new path would fall through to "any logged-in user".

### Migrations

| Version | What it does |
| --- | --- |
| V1 | Initial schema (products, users, the old cart/order join tables) |
| V2 | Seed products and users |
| V3 | `owner` flag → `role` column; hash existing passwords with bcrypt |
| V4 | Remove the Guest user |
| V5 | Prices to `NUMERIC(10,2)` |
| V6 | `orders`, `order_items`, `cart_items`; copy over the old cart and order data |
| V7 | Drop the old join tables |
| V8 | `orders.stripe_session_id`; add `CANCELLED` to the status check |
| V9 | `orders.stripe_payment_intent_id`; add `REFUNDED` to the status check |
| V10 | `product.active` for hiding products (existing rows stay visible) |

Never edit a migration that has already run. Add a new `V<n>__description.sql` instead.

## Roadmap

1. ✅ Spring Boot 4 / Java 25, PostgreSQL + Flyway
2. ✅ Spring Security with roles
3. ✅ Cart and order model, `BigDecimal` money
4. ✅ Stripe Checkout (hosted) and a webhook that marks orders PAID; stock taken when payment succeeds
5. ✅ Admin dashboard, order management (filter, paging, refunds), low-stock alerts, hidden products
6. ⏭️ Automated tests: Spring Boot tests against a Testcontainers PostgreSQL, including the webhook's signature check and duplicate events

## Known limitations

- There are no automated tests yet.
- The cart has no +/− controls; items are added one at a time from the products page.
- Clicking "Pay with Stripe" twice, or in two tabs, creates two PENDING orders, and each could be paid.
- Stock isn't reserved while the customer is on Stripe's page. If it sells out in that window, the payment is refunded automatically and the order is cancelled.
- An unpaid order stays PENDING until Stripe expires its session (24 hours by default).
- Card payments only. Delayed methods such as bank debits (`checkout.session.async_payment_succeeded`) aren't handled.
- A refund made in the Stripe Dashboard doesn't update the order, because `charge.refunded` isn't handled. Refund from the app instead.
- SHIPPED orders can't be refunded; returns aren't modelled.
- "Last 7 days" on the dashboard counts orders by when they were placed, not when they were paid (there's no `paid_at` column).
- The "View payment in Stripe" link points at the test-mode Dashboard.
- Locally, webhooks only arrive while `stripe listen` is running. Events missed while it's off aren't replayed.
