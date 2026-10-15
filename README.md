# Web Dev E-commerce Shop

A small online shop built with Spring Boot and Thymeleaf. Customers can browse products, keep a cart, check out and see their order history. An admin can add and edit products, see every order and mark paid orders as shipped.

The project started as a Team 7 web development group project ([report](docs/report.pdf), [wireframe design](docs/wireframe_design.pdf)). It is now being modernized in phases: the framework was upgraded, and the security, database and order model were rebuilt. The [Then vs now](#then-vs-now) section explains each change.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 25 (Eclipse Temurin) |
| Framework | Spring Boot 4.1.1 (Spring MVC) |
| Views | Thymeleaf server-side templates, plain CSS and JavaScript |
| Security | Spring Security: form login, BCrypt password hashing, `USER`/`ADMIN` roles, CSRF protection |
| Persistence | Spring Data JPA (Hibernate) |
| Database | PostgreSQL 17, run in Docker |
| Schema migrations | Flyway (versioned SQL scripts) |
| Build | Maven 3.9 |
| Monitoring | Spring Boot Actuator (included, not configured yet) |
| Payments | Stripe Checkout (planned, phase 4) |

## Running it locally

### Prerequisites

- JDK 25
- Maven 3.9+
- Docker Desktop (or another Docker engine with Compose)

### Start

```bash
# 1. Start PostgreSQL in the background (defined in compose.yaml)
docker compose up -d

# 2. Start the app. Flyway creates and seeds the schema on first run.
mvn spring-boot:run
```

Then open **<http://localhost:3000>**.

### Seed accounts

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `password` | ADMIN |
| `Phil` | `1234pass` | USER |
| `Joseph` | `w0w` | USER |

### Stop and reset

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

## Features

- **Catalogue:** product list and product detail pages, open to everyone.
- **Accounts:** registration (username of 3+ characters, password of 8+, unique usernames), login and logout.
- **Cart:** one row per product with a quantity. Shows "Only N left" warnings and blocks adding more than is in stock.
- **Checkout:** the order stores each item's price at the time of purchase. Stock is taken all at once, and the whole purchase rolls back if any item has run out.
- **Order history:** customers see their past orders with dates, items and status on their profile.
- **Admin:** add and edit products, view all orders, open an order and mark it as shipped (only allowed for PAID orders).

## Then vs now

The original version worked as a demo but had problems that would break a real shop. Each row below names the old behaviour, what replaced it, and why.

### Platform

| Before | Now | Why |
| --- | --- | --- |
| Spring Boot 2.2.4, Java 13 | Spring Boot 4.1.1, Java 25 | Boot 2.x stopped getting fixes years ago. Moving up also meant switching `javax.*` imports to `jakarta.*`. |
| H2 in-memory database, `ddl-auto=create`, `data.sql` | PostgreSQL in Docker, Flyway migrations, `ddl-auto=validate` | H2 data disappeared on every restart. Now the schema lives in versioned SQL files, and the app refuses to start if the Java entities and the tables disagree. |
| `@Autowired` fields | Constructor injection | Dependencies are explicit and `final`, and the class is easier to test. |
| App on port 8080 | App on port 3000 | |

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
| Stock went down when an item was **added to the cart** (read, subtract, save) | Stock is **checked** at add-to-cart and **taken** at purchase, with one conditional `UPDATE ... WHERE stock >= qty` per line inside a transaction | Abandoned carts no longer lock up stock. Two people buying the last item at the same time can't both succeed. |
| Admin "orders" page listed users and their product lists | Admin sees every order, opens one, and ships it (PAID → SHIPPED) | |

## How it fits together

```text
src/main/java/app/
  SecurityConfig.java          URL rules, login/logout, BCrypt encoder
  AppUserDetailsService.java   loads users from the database for Spring Security
  MainController.java          all page and form handlers
  CartService.java             add/remove cart items, turn a cart into an order (transactional)
  Product, User, CartItem,     JPA entities
  Order, OrderItem, OrderStatus
  *Repository.java             Spring Data repositories (queries, entity graphs, stock decrement)
src/main/resources/
  application.properties       port, datasource, JPA and Flyway settings
  db/migration/V*.sql          Flyway migrations (see below)
  templates/                   Thymeleaf pages (+ fragments/navbar.html)
  static/                      style.css, code.js, product images
compose.yaml                   the PostgreSQL container
```

Design notes:

- **Links run from child to parent only** (`CartItem → User`, `OrderItem → Order`). A user's cart and orders are loaded through repositories, not through collections on `User`.
- **`spring.jpa.open-in-view=false`:** the database session closes before the template renders. Each query states what it needs with `@EntityGraph`, so a template can't trigger extra hidden queries.
- **Stock is decremented in SQL** (`ProductRepository.decrementStock`), so the check and the update happen in one atomic step. Purchases lock products in id order so two checkouts can't deadlock.

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

Never edit a migration that has already run. Add a new `V<n>__description.sql` instead.

## Roadmap

1. ✅ Spring Boot 4 / Java 25, PostgreSQL + Flyway
2. ✅ Spring Security with roles
3. ✅ Cart and order model, `BigDecimal` money
4. ⏭️ Stripe Checkout (hosted) and a webhook that marks orders PAID; stock taken when payment succeeds
5. Admin dashboard, order management, low-stock alerts

## Known limitations

- A missing product id on `/details/{id}` or `/edit/{id}` shows a 500 error instead of a 404.
- The cart has no +/− controls; items are added one at a time from the products page.
- Checkout marks orders as PAID straight away. Real payment may be implemented in the future.
