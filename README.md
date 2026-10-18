# Web Dev E-commerce Shop

A small online shop built with Spring Boot and Thymeleaf. Customers can browse and filter products, add them to a cart or buy one straight away, pay through Stripe and see their order history. An admin gets a sales dashboard with low-stock alerts, can filter and page through every order, ship or refund paid orders, and add, edit, hide or set pictures for products.

The project started as a web development group project for University ([report](docs/report.pdf), [wireframe design](docs/wireframe_design.pdf)). I decided to see if I could update, further refine and implement features as practice. The framework was upgraded, the security, database and order model were rebuilt, real payments were added, the Bootstrap pages were rebuilt with Tailwind CSS, and the whole thing is now covered by automated tests. The [Then vs now](#then-vs-now) section explains each change.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 25 (Eclipse Temurin) |
| Framework | Spring Boot 4.1.1 (Spring MVC) |
| Views | Thymeleaf server-side templates with one shared layout; plain JavaScript (`fetch`) for the cart toast and the product filters |
| Styling | Tailwind CSS v4, compiled at build time into one stylesheet (no CDN); Heroicons as inline SVG |
| Security | Spring Security: form login, BCrypt password hashing, `USER`/`ADMIN` roles, CSRF protection |
| Persistence | Spring Data JPA (Hibernate) |
| Validation | Bean Validation (Hibernate Validator) on request records |
| Database | PostgreSQL 17, running in Docker |
| Schema migrations | Flyway (versioned SQL scripts) |
| Build | Maven 3.9; `frontend-maven-plugin` downloads Node and runs the Tailwind CLI during the build |
| Payments | Stripe Checkout (hosted payment page) and a signed webhook, via `stripe-java` |
| Testing | JUnit 6, AssertJ, Mockito, Spring MockMvc with `spring-security-test`, Testcontainers (PostgreSQL 17) |
| Formatting | Spotless: palantir-java-format for Java, whitespace rules for templates, CSS, JS, SQL and properties; checked by `mvn verify` |

## Run it locally

### Prerequisites

- JDK 25
- Maven 3.9+
- Docker Desktop (or another Docker engine with Compose), for the database and for the tests
- A Stripe account in test mode (a sandbox) and the [Stripe CLI](https://docs.stripe.com/stripe-cli)
- No Node.js: Maven downloads its own copy to build the stylesheet. Only the live-CSS loop below uses a local Node 24 and npm.

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

### Edit templates and CSS without restarting

```powershell
# Serve templates straight from src/ and don't cache them
mvn spring-boot:run "-Dspring-boot.run.arguments=--spring.thymeleaf.prefix=file:src/main/resources/templates/ --spring.thymeleaf.cache=false"

# In another terminal: rebuild app.css whenever a template or app.css changes
cd src/main/frontend; npm run watch
```

Save, then refresh the browser. Java, `code.js` and image changes still need a restart. Tailwind only generates classes it finds in saved templates and `code.js`, so always write full class names (never build them from pieces).

### Seed accounts

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `password` | ADMIN |
| `Phil` | `1234pass` | USER |
| `Joseph` | `josephw0w` | USER |
| `Maria` | `maria2026` | USER |
| `Kenji` | `kenji2026` | USER |

The seed also has 50 products across 9 types, 15 of them with a picture (the rest show a placeholder). A few are at or below the low-stock threshold, one is out of stock and one is hidden, so every part of the dashboard has something to show.

### Stop and reset the database

```bash
# Stop the app: Ctrl+C in the terminal running mvn

docker compose down       # stop the database, keep its data
docker compose down -v    # stop and wipe the data; Flyway rebuilds it from V1 next start
```

A database created before the migrations were squashed (see [Migrations](#migrations)) won't start: Flyway finds migrations it no longer has. Wipe it once with `docker compose down -v`, and run `mvn clean` so old migration files don't linger in `target/classes`.

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

## Testing

```powershell
mvn verify                          # all 200 tests, then the Spotless format check
mvn test "-Dtest=CartServiceTest"   # one class
mvn spotless:apply                  # reformat the code; run this before verify if the check fails

# Only the tests that need no Docker:
mvn test "-Dtest=OrderTest,SalesTotalsTest,ProductValidationTest"
```

- **Docker must be running.** Testcontainers starts a throwaway PostgreSQL 17 on a random port and Flyway migrates it, so every run also checks the migrations against an empty database. The development database doesn't need to be up and is never touched.
- **No Stripe account is needed.** `StripeClient` is a Mockito mock in every Spring test, and `src/test/resources/application-test.properties` supplies dummy keys. If `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` are set, Spring uses them instead (environment variables beat properties files). The tests work either way, because nothing ever reaches Stripe.
- **All Spring tests share one context and one container.** The `@SpringBootTest` classes carry identical annotations and mock beans, so Spring builds the application once. MockMvc is built from that context in each class instead of with `@AutoConfigureMockMvc`, which would start a second one.
- **Tests work alongside the seed data instead of relying on it.** They create their own `Test ...` products and `test-...` users, check properties of the result ("contains mine", "sorted") rather than exact seed contents, and delete their rows afterwards.

| Layer | Classes | What they prove |
| --- | --- | --- |
| Unit (62 tests, no Spring) | `OrderTest`, `SalesTotalsTest`, `ProductValidationTest` | Every order status change allowed or refused; revenue sums; every validation limit on the product records, at the boundary, including the picture path rule |
| Repository (26, `@DataJpaTest`) | `ProductRepositoryTest`, `OrderRepositoryTest`, `CartItemRepositoryTest` | The conditional stock `UPDATE` (enough, the last unit, not enough), the shop and low-stock lists, paging by status, the dashboard aggregates, the cart count, the picture column's `CHECK`, and that every picture path names a real file (case included) |
| Service (14, `@SpringBootTest`) | `CartServiceTest`, `MyApplicationTests` | Cart → PENDING order → PAID with real commits and rollbacks: stock taken once, everything rolled back when stock runs out, and a second refund waits for the first one's row lock |
| Web (98, MockMvc) | `PaymentWebTest` | The webhook with real signatures (valid → 200; wrong secret, changed body or missing header → 400), a duplicate event taking stock once, the automatic refund when stock ran out, the admin refund's idempotency key |
| | `SecurityConfigTest` | Every admin URL sends visitors to `/login` and refuses customers with 403; a POST without a valid CSRF token is refused, even for an admin; the stylesheet is public |
| | `MainControllerTest` | 404 for missing and hidden products, 400 for bad JSON or an unknown status, `?invalid` for a bad edit form, 409 for shipping or refunding an order in the wrong state; the add-to-cart JSON (new count, 409 without stock), Buy landing on checkout, the cart count on every page, the type chips, setting and clearing a picture |

## Features

- **Catalogue:** a card grid, open to everyone, with each product's picture (or a placeholder), price and an "Only N left" or "Out of stock" hint. Cards lift slightly on hover. A name search and one chip per type filter the list together. Products an admin has hidden don't appear, and their pages return 404.
- **Add to cart or buy:** Add To Cart stays on the page: a small toast shows the product, and the cart badge in the navbar updates. Buy adds the product and goes straight to checkout. Both buttons are disabled when a product is out of stock.
- **One layout:** every page shares a sticky navbar (icon links with labels on hover and for screen readers) and a footer, and works on phones.
- **Accounts:** registration (username of 3+ characters, password of 8+, unique usernames), login and logout.
- **Cart:** one row per product with a quantity. Shows "Only N left" and "No longer sold" warnings, and blocks adding more than is in stock.
- **Checkout and payment:** "Pay with Stripe" creates a PENDING order with each item's current price and sends the customer to Stripe's hosted payment page. When Stripe's webhook confirms the payment, the stock is taken, the order becomes PAID and those products leave the cart. If the customer never pays, the order is CANCELLED when the session expires. If stock ran out while they were paying, the payment is refunded automatically.
- **Order history:** customers see their past orders with dates, items and status (PENDING, PAID, SHIPPED, CANCELLED, REFUNDED) on their profile.
- **Admin dashboard** (`/owners`): revenue all time and for the last 7 days, order counts by status (each links to the filtered list), low-stock products, and the 10 most recent orders.
- **Order management:** every order, 20 per page, filterable by status. An order's page links to its payment in the Stripe Dashboard. PAID orders can be marked as shipped, or refunded: the money goes back through Stripe and the stock returns to the shelf.
- **Product management:** a product table with thumbnails and the same filters. Add and edit products, including an optional picture (a path in the app or an image URL), with server-side validation. Products can be hidden from the shop and shown again; they're never deleted, so old orders keep showing what was bought.

## Then vs now

The original version worked as a demo but had problems that would break a real shop. Open a category to see each change: the old behaviour, what replaced it, and why.

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>Platform</strong>: Spring Boot 2.2 → 4.1, H2 → PostgreSQL with Flyway, feature packages</summary>

| Before | Now | Why |
| --- | --- | --- |
| Spring Boot 2.2.4, Java 13 | Spring Boot 4.1.1, Java 25 | Boot 2.x stopped getting fixes years ago. Moving up also meant switching `javax.*` imports to `jakarta.*`. |
| H2 in-memory database, `ddl-auto=create`, `data.sql` | PostgreSQL in Docker, Flyway migrations, `ddl-auto=validate` | H2 data disappeared on every restart. Now the schema lives in versioned SQL files, and the app refuses to start if the Java entities and the tables disagree. |
| `@Autowired` fields | Constructor injection | Dependencies are explicit and `final`, and the class is easier to test. |
| Every class in one flat package | Feature packages: `admin`, `cart`, `order`, `payment`, `product`, `security`, `user` | Everything about one feature sits together. |

</details>

<details>
<summary><strong>Security</strong>: a session per browser, BCrypt passwords, roles, CSRF protection</summary>

| Before | Now | Why |
| --- | --- | --- |
| The logged-in user was kept in fields on `MainController` (`loggedUser`, `loggedIn`) | Spring Security keeps a session per browser, and the controller reads the user from `Principal` | A controller is a single shared object. When one person logged in, **every visitor** was logged in as them. The admin edit form had the same bug through a shared `tempProduct` field. |
| Plain-text passwords, checked by loading every user and comparing strings | BCrypt hashes, checked by Spring Security | Stored passwords can't be read back. The seed stores BCrypt hashes, made with the same encoder the app uses. |
| `owner` boolean checked inside some handlers; `/owners`, `/product/add` and `/view/orders` had no check | `role` column (`USER`/`ADMIN`); admin URLs locked in `SecurityConfig` | Access rules live in one place and can't be skipped by forgetting an `if`. |
| A "Guest" row in the users table (id 1) stood in for logged-out visitors | Removed; logged out simply means no session | A fake account that every anonymous visitor shared. |
| State changes over `GET` (`/purchase`, `/cart/remove`, add to cart); no CSRF protection | `POST` with a CSRF token (AJAX calls send it in a header) | A link or image on another site could empty a cart or place an order on the user's behalf. |
| No registration rules; duplicate usernames possible | Length rules, `UNIQUE` username in the database, friendly error messages | |

</details>

<details>
<summary><strong>Cart, orders and money</strong>: <code>BigDecimal</code> prices, real orders, stock taken only when payment succeeds</summary>

| Before | Now | Why |
| --- | --- | --- |
| `Double` prices | `BigDecimal` in Java, `NUMERIC(10,2)` in the database | Floating point can't represent values like 0.10 exactly, so totals drift. |
| Cart = a many-to-many list of products; the same product repeated for each copy | `cart_items` table with a `quantity`, one row per user and product | Quantities are real numbers, and adding the same item twice doesn't create duplicate rows. |
| Orders = one flat list of products per user, with no date, status or price | `orders` + `order_items`, each with `created_at`, `status` and the `unit_price` at purchase time | Purchases are grouped, and a later price change doesn't rewrite order history. |
| Stock went down when an item was **added to the cart** (read, subtract, save) | Stock is **checked** at add-to-cart and **taken** when Stripe confirms payment, with one conditional `UPDATE ... WHERE stock >= qty` per line inside a transaction | Abandoned carts no longer lock up stock. Two people buying the last item at the same time can't both succeed. |
| Admin "orders" page listed users and their product lists | Admin sees every order, opens one, and ships it (PAID → SHIPPED) | |

</details>

<details>
<summary><strong>Payment and checkout</strong>: Stripe's hosted page, and a signed webhook that confirms payment</summary>

| Before | Now | Why |
| --- | --- | --- |
| "Confirm Purchase" just moved the cart into orders; the billing form was never saved | Stripe Checkout's hosted page takes the payment; the app never sees card details | Real payments without handling card data, which stays with Stripe. |
| An order counted as done as soon as the button was clicked | The order stays PENDING until a **signed webhook** from Stripe confirms payment | A browser landing on the "thank you" page proves nothing, because anyone can open that URL. Only Stripe's signed event can be trusted. |

</details>

<details>
<summary><strong>Admin</strong>: a dashboard, paged orders, refunds, hidden products, server-side validation</summary>

| Before | Now | Why |
| --- | --- | --- |
| The admin page had Log Out, View Orders and a "Change Orders" button that only went to `/` | A dashboard: revenue, orders by status, low-stock products, recent orders | The numbers come from `COUNT`/`SUM ... GROUP BY` queries mapped straight into records, so the page costs four small queries however many orders exist. |
| `/view/orders` loaded every order on every visit | 20 per page (`Pageable`), filterable by `?status=` | One page is a `LIMIT/OFFSET` query plus a count, so the page stays fast as orders grow. |
| No refunds; an admin could only ship | Refund for PAID orders: a Stripe refund, then status REFUNDED and the stock goes back | Stripe is called first, with an idempotency key, so clicking again after a failure can never refund twice. CANCELLED still means "never paid", so revenue leaves out both. |
| A product couldn't be removed at all | Products can be hidden and shown again (`active` flag) | `order_items` references products, so deleting one would break order history. Hidden products leave the shop, the cart's checkout and the low-stock list. |
| Bad product input (negative price, empty name) hit a database `CHECK` and showed a 500; a price of `1.999` was silently rounded | Bean Validation on request records; the edit form shows a message | The browser's `min`/`step` checks are easy to remove, so the server checks too. |
| "Add product" bound the JSON straight to the `Product` entity | A `NewProductRequest` record with no `id` field | A request containing `"id": 3` overwrote product 3 instead of adding a new one. |
| A missing product (`/details/999`) showed a 500 | A 404 page | `ProductNotFoundException` carries `@ResponseStatus(NOT_FOUND)`. |

</details>

<details>
<summary><strong>Frontend</strong>: Bootstrap from CDNs → Tailwind built with Maven, one layout, product cards</summary>

| Before | Now | Why |
| --- | --- | --- |
| Bootstrap 4.5, jQuery, Popper and Font Awesome from CDNs, plus a `style.css` | Tailwind CSS v4, compiled at build time into one `app.css` that only holds the classes the templates use | No third-party downloads at page load. Bootstrap's rules would also beat Tailwind's (Tailwind uses cascade layers), so the two couldn't share a page. |
| Every page repeated its own `<head>`: stylesheets, scripts, favicon | One layout fragment holds the `<head>`, navbar and footer; each page only supplies its title and content | Adding a script or stylesheet is one edit instead of one per page. |
| Text links, and Font Awesome icons for the account and cart links | Heroicons inline SVGs; each link's label shows on hover or keyboard focus and is also its accessible name | No icon font to download, and icon-only links still have a name for screen readers. |
| Products in a table; a picture only on the details page, looked up by product name | A card grid; a `product.image` column holds a path or URL, and `NULL` shows a placeholder | 42 of the 50 products had no file with their exact name, so their pages showed a broken image. |
| Add To Cart reloaded the page with no feedback | It posts with `fetch`; a toast shows the product and the navbar's cart badge updates. A Buy button goes straight to checkout | Customers see what happened without losing their place. Without JavaScript the form still posts normally. |
| Two search boxes (type and name); whichever was typed in last won | One name search plus a chip per type, combined | Customers don't have to know the type names. |
| Prices under €1 showed as "€.99" | "€0.99" | Thymeleaf's `formatDecimal` was told to print at least 0 digits before the point. |
| The first page of a new session linked to `app.css;jsessionid=...` | `server.servlet.session.tracking-modes=cookie` | Spring Security rejects URLs with `;` in them, so that page loaded without its stylesheet or script. |

</details>

<details>
<summary><strong>Tests and tooling</strong>: 200 tests against a real PostgreSQL, Spotless formatting, ten migrations squashed into two</summary>

| Before | Now | Why |
| --- | --- | --- |
| No automated tests; every change was checked by clicking through the site | 200 tests in four layers, run by `mvn verify` (see [Testing](#testing)) | The payment flow has cases nobody clicks through by hand: duplicate webhooks, stock running out mid-payment, two refunds at once. |
| — | Tests run against a real PostgreSQL in Docker (Testcontainers), not an in-memory database | Row locks (`SELECT ... FOR UPDATE`), `CHECK` constraints and the Flyway migrations behave exactly as in the app. An in-memory stand-in would only imitate them. |
| Two refund clicks at the same moment could both put the stock back | `markRefunded` locks the order row, like `completePayment` | Found while writing the tests. Stripe refunded only once (same idempotency key), but the shelf ended up with stock that didn't exist. |
| No formatting rules | Spotless (palantir-java-format) fails the build on unformatted code | Style stops being a review topic. The one big formatting commit is listed in `.git-blame-ignore-revs`, so `git blame` skips it. |
| Ten migrations, one per change made along the way | Squashed into V1 (schema) and V2 (seed) | Nothing had been deployed, so no database needed the step-by-step history. Two files are easier to read than ten that undo each other. |

</details>

<!-- markdownlint-enable MD033 -->

## Project tree

```text
src/main/java/app/
├── MyApplication.java                  Spring Boot entry point
├── MainController.java                 every page and form handler
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
│   ├── ProductRepository.java          stock decrement and increment in SQL
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
├── db/migration/                       V1 schema, V2 seed, V3-V4 product pictures (Flyway)
├── templates/                          Thymeleaf pages, error/404 page
│   └── fragments/                      layout, navbar, footer, icons, cart toast, product picture, filters
└── static/                             code.js, product images, logo

src/main/frontend/                      the stylesheet's source; built into target/, never committed
├── app.css                             Tailwind entry: files to scan, heading defaults, shared components
└── package.json                        exact Tailwind CLI version; Maven runs npm ci and npm run build

src/test/java/app/
├── TestContainerConfiguration.java     throwaway PostgreSQL 17 for the tests
├── TestData.java                       valid unsaved products and users
├── MyApplicationTests.java             the application starts
├── MainControllerTest.java             pages, cart count, add to cart, Buy, bad input
├── cart/                               CartServiceTest, CartItemRepositoryTest
├── order/                              OrderTest, SalesTotalsTest, OrderRepositoryTest
├── payment/                            PaymentWebTest
├── product/                            ProductValidationTest, ProductRepositoryTest
└── security/                           SecurityConfigTest

src/test/resources/
└── application-test.properties         dummy Stripe keys for the test profile

compose.yaml                            the PostgreSQL container
.git-blame-ignore-revs                  formatting commits git blame skips
```

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>Design notes</strong>: relations, transactions, locking, Stripe calls and refunds</summary>

- **Links run from child to parent only** (`CartItem → User`, `OrderItem → Order`). A user's cart and orders are loaded through repositories, not through collections on `User`.
- **`spring.jpa.open-in-view=false`:** the database session closes before the template renders. Each query states what it needs with `@EntityGraph`, so a template can't trigger extra hidden queries.
- **Stock is decremented in SQL** (`ProductRepository.decrementStock`), so the check and the update happen in one atomic step. Payments lock products in id order so two of them can't deadlock.
- **Stripe can deliver the same event twice, even at the same time.** `CartService.completePayment` locks the order row (`SELECT ... FOR UPDATE`) and only acts on a PENDING order, so a repeated event never takes stock twice. `markRefunded` does the same for refunds.
- **No database transaction stays open during a Stripe call.** The order is saved first, then the Checkout Session is created, then its id is saved. Refunds run after the stock transaction has rolled back.
- **Amounts go to Stripe in cents** with `movePointRight(2).longValueExact()`, with no floating-point step in between.
- **Admin refunds call Stripe first, then the database.** If the database step fails, the order still says PAID, and clicking Refund again is safe: the same idempotency key makes Stripe return the existing refund. The other order would risk an order marked REFUNDED whose customer never got their money.
- **Paging only fetches to-one relations.** The order list's `@EntityGraph` loads `user` but not `items`. Fetching a collection would make Hibernate page in memory, which loads every row again.
- **The stylesheet is build output.** The Tailwind CLI scans the templates and `code.js` and writes only the classes it finds to `target/classes/static/app.css`. A class name assembled at runtime would never be found, so templates always spell out full class names.
- **One URL, two answers for add to cart.** A plain form post redirects back to the products page. `code.js` sends `X-Requested-With: XMLHttpRequest`, which picks a second mapping that answers JSON with the new cart count (409 when there's no stock left). The header decides, not `Accept`, because browsers send `Accept: */*`.
- **The picture rule lives in two places.** A `CHECK` on `product.image` guards the data; `@Pattern` on the form records turns a bad value into a 400 or a form message instead of a database error (500).
- **Admin endpoints live under paths `SecurityConfig` already locks** (for example `POST /edit/{id}/active`). A new path would fall through to "any logged-in user".

</details>

<!-- markdownlint-enable MD033 -->

### Migrations

| Version | What it does |
| --- | --- |
| V1 | The schema: `product`, `users`, `orders`, `order_items`, `cart_items`, with their constraints |
| V2 | Seed data: 50 products and the 5 accounts above (BCrypt hashes) |
| V3 | Adds `product.image` (a path or URL, `NULL` for none, with a `CHECK`) and sets it for the 8 seed products whose picture file has their name |
| V4 | Points 7 more seed products at picture files that have older names |

## To-Do

- A real visual design: the emerald and stone colours are Tailwind's defaults, kept as placeholders.
- CI (GitHub Actions running `mvn verify`) and automated dependency updates once the repository is public. Tailwind and `stripe-java` are pinned by hand until then.

## Known limitations

- Registering with a username longer than 50 characters shows a 500 error instead of a message: the length is checked by the database, not the form.
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
- Pictures are set as a path or URL; there's no upload. A URL that stops working shows a broken image on the product pages (only the cart toast falls back to the placeholder).
- Error pages show no cart badge, because they're rendered outside `MainController`.
- The add-to-cart toast hides after 4 seconds, even while the pointer is on it.
- `package.json` overrides `@parcel/watcher` to 2.6.0 so `npm audit` is clean. Remove the override once a newer Tailwind CLI depends on it.
