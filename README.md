# Etelier

A small online shop built with Spring Boot and Thymeleaf. Customers can browse and filter products, add them to a cart or buy one straight away, pay through Stripe and see their order history. An admin gets a sales dashboard with low-stock alerts, can filter and page through every order, ship or refund paid orders, and add, edit, hide or set pictures for products.

The project started as a web development group project for University ([report](docs/report.pdf), [wireframe design](docs/wireframe_design.pdf)). I decided to see if I could update, further refine and implement features as practice. The framework was upgraded, the security, database and order model were rebuilt, real payments were added, the Bootstrap pages were rebuilt with Tailwind CSS and then redesigned as **Etelier**, the catalogue was replaced with 194 real-looking products, and the whole thing is now covered by automated tests. The [Then vs now](#then-vs-now) section explains each change.

## Tech stack

| Layer | Choice |
| --- | --- |
| Language | Java 25 (Eclipse Temurin) |
| Framework | Spring Boot 4.1.1 (Spring MVC) |
| Views | Thymeleaf server-side templates with one shared layout; plain JavaScript (`fetch`) for the cart toast and the product filters |
| Styling | Tailwind CSS v4, compiled at build time into one stylesheet (no CDN), with the design's colours, type sizes and radii as `@theme` tokens; Heroicons as inline SVG |
| Fonts | Geist (headings) and Inter (text), self-hosted as WOFF2 and cached for a year |
| Security | Spring Security: form login, BCrypt password hashing, `USER`/`ADMIN` roles, CSRF protection |
| Persistence | Spring Data JPA (Hibernate) |
| Validation | Bean Validation (Hibernate Validator) on request records |
| Database | PostgreSQL 17, running in Docker |
| Schema migrations | Flyway (versioned SQL scripts) |
| Seed data | [DummyJSON](https://dummyjson.com/docs/products)'s product catalogue, turned into a migration by a Node script |
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
- An internet connection: product pictures load from DummyJSON's CDN
- No Node.js: Maven downloads its own copy to build the stylesheet. Only the live-CSS loop and the seed script below use a local Node 24 and npm.

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

Save, then refresh the browser. Java, `code.js`, image and font changes still need a restart. Tailwind only generates classes it finds in saved templates and `code.js`, so always write full class names (never build them from pieces).

### Seed accounts

| Username | Password | Role |
| --- | --- | --- |
| `owner` | `password` | ADMIN |
| `Phil` | `1234pass` | USER |
| `Joseph` | `josephw0w` | USER |
| `Maria` | `maria2026` | USER |
| `Kenji` | `kenji2026` | USER |

### Seed products

The shop is stocked with DummyJSON's 194 sample products in 24 types, with pictures hot-linked from `cdn.dummyjson.com`.

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>What's in the seed, and how to regenerate it</strong></summary>

Besides pictures, each product has a brand, rating, discount, gallery and tags, and there are 582 reviews. Four products are out of stock, several are at or below the low-stock threshold, and one (a duplicate "Rolex Cellini Moonphase") is hidden, so every part of the dashboard and the admin list has something to show.

`V3__dummyjson_products.sql` is generated and committed, so builds and tests never call DummyJSON. To regenerate it (only for a fresh database, because Flyway refuses an applied migration that changed):

```powershell
node scripts/dummyjson-to-sql.mjs
docker compose down -v; mvn clean
```

</details>

<!-- markdownlint-enable MD033 -->

### Stop and reset the database

```bash
# Stop the app: Ctrl+C in the terminal running mvn

docker compose down       # stop the database, keep its data
docker compose down -v    # stop and wipe the data; Flyway rebuilds it from V1 next start
```

A database created before the migrations were squashed into V1-V3 won't start: Flyway finds migrations it no longer has. Wipe it once with `docker compose down -v`, and run `mvn clean` so old migration files don't linger in `target/classes`.

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
| `app.low-stock-threshold` | `13` | "Only N left" hints, Low badges and the dashboard's low-stock list |

## Testing

```powershell
mvn verify                          # all 240 tests, then the Spotless format check
mvn test "-Dtest=CartServiceTest"   # one class
mvn spotless:apply                  # reformat the code; run this before verify if the check fails

# Only the tests that need no Docker:
mvn test "-Dtest=OrderTest,SalesTotalsTest,ProductValidationTest"
```

- **Docker must be running:** the tests use a throwaway PostgreSQL 17, never the development database.
- **No Stripe account is needed:** Stripe is mocked, and the test profile has dummy keys.

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>How the tests are set up</strong></summary>

- **A real database per run.** Testcontainers starts PostgreSQL 17 on a random port and Flyway migrates it, so every run also checks the migrations against an empty database.
- **Nothing reaches Stripe.** `StripeClient` is a Mockito mock in every Spring test, and `src/test/resources/application-test.properties` supplies dummy keys. If `STRIPE_SECRET_KEY` / `STRIPE_WEBHOOK_SECRET` are set, Spring uses them instead (environment variables beat properties files); the tests work either way.
- **All Spring tests share one context and one container.** The `@SpringBootTest` classes carry identical annotations and mock beans, so Spring builds the application once. MockMvc is built from that context in each class instead of with `@AutoConfigureMockMvc`, which would start a second one.
- **Tests work alongside the seed data instead of relying on it.** They create their own `Test ...` products and `test-...` users, check properties of the result ("contains mine", "sorted", "one more than before") rather than exact seed contents, and delete their rows afterwards.
- **Tests read configured values** (the low-stock threshold, the number of featured products) instead of repeating them, so changing a setting can't turn the build red.
- **Every page test renders its template.** MockMvc runs Thymeleaf, so a broken expression fails the test even though no test looks at the HTML; the tests check the model the page is given.

</details>

<details>
<summary><strong>What each layer tests</strong>: 62 unit, 38 repository, 14 service and 126 web tests</summary>

| Layer | Classes | What they prove |
| --- | --- | --- |
| Unit (62 tests, no Spring) | `OrderTest`, `SalesTotalsTest`, `ProductValidationTest` | Every order status change allowed or refused; revenue sums; every validation limit on the product records, at the boundary, including the picture path rule |
| Repository (38, `@DataJpaTest`) | `ProductRepositoryTest`, `ReviewRepositoryTest`, `OrderRepositoryTest`, `CartItemRepositoryTest` | The conditional stock `UPDATE` (enough, the last unit, not enough), the shop list, the featured list (best rated first, unrated last, no hidden or sold-out products), the low-stock list and its count, paging by status, the dashboard aggregates, the cart count, reviews newest first, the picture and rating `CHECK`s, and that every local picture path names a real file (case included) |
| Service (14, `@SpringBootTest`) | `CartServiceTest`, `MyApplicationTests` | Cart → PENDING order → PAID with real commits and rollbacks: stock taken once, everything rolled back when stock runs out, and a second refund waits for the first one's row lock |
| Web (126, MockMvc) | `PaymentWebTest` | The webhook with real signatures (valid → 200; wrong secret, changed body or missing header → 400), a duplicate event taking stock once, the automatic refund when stock ran out, the admin refund's idempotency key |
| | `SecurityConfigTest` | Every admin URL sends visitors to `/login` and refuses customers with 403; a POST without a valid CSRF token is refused, even for an admin; the stylesheet and every font it names are public, and fonts are cached for a year |
| | `MainControllerTest` | 404 for missing and hidden products, 400 for bad JSON or an unknown status, the edit form shown again with the failing field marked, 409 for shipping or refunding an order in the wrong state; the add-to-cart JSON (new count, 409 without stock), Buy landing on checkout, the cart count and the current path (for the navbar) on every page, Home's featured products, the type chips and the hidden count, the cart total, the profile's orders newest first, the dashboard's low-stock list, the order list's filter and pages, every status's order page, setting and clearing a picture |

</details>

<!-- markdownlint-enable MD033 -->

## Features

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

- **Home:** a welcome line and a Featured row with the four best-rated products in stock.
- **Catalogue:** every product in a card grid, filtered by a name search and a chip per type.
  <details>
  <summary>Cards, filters and hidden products</summary>

  Each card shows the product's picture (or a placeholder), its price and an "Only N left" or "Out of stock" hint, and lifts slightly on hover. The search and the chips filter together, and the count above the grid follows ("12 of 194"). Products an admin has hidden don't appear, and their pages return 404.

  </details>

- **Product page:** a large picture with the details, price and stock. On phones, Add To Cart and Buy stay pinned to the bottom of the screen.
- **Add to cart or buy:** Add To Cart keeps you on the page; Buy goes straight to checkout.
  <details>
  <summary>The toast and the cart badge</summary>

  Adding a product shows a small toast with its picture and name, and the cart badge in the navbar updates. Without JavaScript the form still posts normally. Both buttons are disabled when a product is out of stock.

  </details>

- **One layout:** a sticky navbar that marks the current page, and a footer. On phones, the navbar's links become icons.
- **Accounts:** register, log in and log out.
  <details>
  <summary>Registration rules</summary>

  Usernames need 3+ characters and must be unique; passwords need 8+. Every field has a visible label, the password rule is shown up front, and a field that's wrong gets a message under it.

  </details>

- **Cart:** one line per product, stock warnings and the total.
  <details>
  <summary>What a cart line shows</summary>

  Each line shows the picture, the price each and the quantity, with an "Only N left" or "No longer sold" warning when needed. Adding more than is in stock is blocked. On phones the total and the checkout button stay pinned to the bottom of the screen.

  </details>

- **Checkout and payment:** pay on Stripe's hosted page; Stripe's webhook confirms the order.
  <details>
  <summary>How an order goes from PENDING to PAID</summary>

  "Pay with Stripe" creates a PENDING order with each item's current price and sends the customer to Stripe. When Stripe's webhook confirms the payment, the stock is taken, the order becomes PAID and those products leave the cart. If the customer never pays, the order is CANCELLED when the session expires. If stock ran out while they were paying, the payment is refunded automatically.

  </details>

- **Order history:** past orders on the profile, newest first, each with a colour badge for its status.
- **Admin dashboard** (`/owners`): revenue, orders by status, recent orders and low stock.
  <details>
  <summary>What the dashboard shows</summary>

  Revenue all time and for the last 7 days, order counts by status (each links to the filtered list), the 10 most recent orders next to the 10 emptiest products (with how many are low in total), and a form to add a product.

  </details>

- **Order management:** every order, filtered by status, with shipping and refunds.
  <details>
  <summary>Paging, the order page and refunds</summary>

  Orders come 20 per page, filtered with status chips. An order's page shows its items next to its facts and actions, and links to its payment in the Stripe Dashboard. PAID orders can be marked as shipped, or refunded: the money goes back through Stripe and the stock returns to the shelf.

  </details>

- **Product management:** add, edit, hide and show products, and set their pictures.
  <details>
  <summary>The product table and the edit form</summary>

  The admin's product table has thumbnails, the same filters as the catalogue and a count of hidden products. A picture is a path in the app or an image URL. The server validates every edit: a refused one comes back with what was typed and a message under each failing field. Products are never deleted, only hidden, so old orders keep showing what was bought.

  </details>

<!-- markdownlint-enable MD033 -->

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
| The admin page had Log Out, View Orders and a "Change Orders" button that only went to `/` | A dashboard: revenue, orders by status, low-stock products, recent orders | The numbers come from `COUNT`/`SUM ... GROUP BY` queries mapped straight into records, so the page costs a few small queries however many orders exist. |
| `/view/orders` loaded every order on every visit | 20 per page (`Pageable`), filterable by `?status=` | One page is a `LIMIT/OFFSET` query plus a count, so the page stays fast as orders grow. |
| No refunds; an admin could only ship | Refund for PAID orders: a Stripe refund, then status REFUNDED and the stock goes back | Stripe is called first, with an idempotency key, so clicking again after a failure can never refund twice. CANCELLED still means "never paid", so revenue leaves out both. |
| A product couldn't be removed at all | Products can be hidden and shown again (`active` flag) | `order_items` references products, so deleting one would break order history. Hidden products leave the shop, the cart's checkout and the low-stock list. |
| Bad product input (negative price, empty name) hit a database `CHECK` and showed a 500; a price of `1.999` was silently rounded | Bean Validation on request records; a refused edit form comes back with what was typed and a message under each failing field | The browser's `min`/`step` checks are easy to remove, so the server checks too. The form is shown again rather than redirected, because the list of failing fields (`BindingResult`) doesn't survive a redirect. |
| "Add product" bound the JSON straight to the `Product` entity | A `NewProductRequest` record with no `id` field | A request containing `"id": 3` overwrote product 3 instead of adding a new one. |
| A missing product (`/details/999`) showed a 500 | A 404 page | `ProductNotFoundException` carries `@ResponseStatus(NOT_FOUND)`. |
| The low-stock list showed every product under the threshold | The 10 emptiest, plus how many there are in total | With 194 products the list would have filled the page. A `Limit` parameter and a `countBy...` query keep it to two small queries. |

</details>

<details>
<summary><strong>Frontend</strong>: Bootstrap from CDNs → Tailwind built with Maven, one layout, product cards</summary>

| Before | Now | Why |
| --- | --- | --- |
| Bootstrap 4.5, jQuery, Popper and Font Awesome from CDNs, plus a `style.css` | Tailwind CSS v4, compiled at build time into one `app.css` that only holds the classes the templates use | No third-party downloads at page load. Bootstrap's rules would also beat Tailwind's (Tailwind uses cascade layers), so the two couldn't share a page. |
| Every page repeated its own `<head>`: stylesheets, scripts, favicon | One layout fragment holds the `<head>`, navbar and footer; each page only supplies its title and content | Adding a script or stylesheet is one edit instead of one per page. |
| Text links, and Font Awesome icons for the account and cart links | Heroicons inline SVGs; each icon link's label shows on hover or keyboard focus and is also its accessible name | No icon font to download, and icon-only links still have a name for screen readers. |
| Products in a table; a picture only on the details page, looked up by product name | A card grid; a `product.image` column holds a path or URL, and `NULL` shows a placeholder | 42 of the original 50 products had no file with their exact name, so their pages showed a broken image. |
| Add To Cart reloaded the page with no feedback | It posts with `fetch`; a toast shows the product and the navbar's cart badge updates. A Buy button goes straight to checkout | Customers see what happened without losing their place. Without JavaScript the form still posts normally. |
| Two search boxes (type and name); whichever was typed in last won | One name search plus a chip per type, combined | Customers don't have to know the type names. |
| Prices under €1 showed as "€.99" | "€0.99" | Thymeleaf's `formatDecimal` was told to print at least 0 digits before the point. |
| The first page of a new session linked to `app.css;jsessionid=...` | `server.servlet.session.tracking-modes=cookie` | Spring Security rejects URLs with `;` in them, so that page loaded without its stylesheet or script. |

</details>

<details>
<summary><strong>Design</strong>: Tailwind's placeholder colours → the Etelier look, page by page</summary>

| Before | Now | Why |
| --- | --- | --- |
| Tailwind's default emerald and stone colours and the system font, kept as placeholders | Etelier: a cream canvas, white cards with a 2px border, black ink, mint only as a fill, pill-shaped buttons and chips; Geist for headings, Inter for text | One consistent look, designed as page mockups first and then built page by page. The colours, type sizes, radii and shadows are `@theme` tokens, so a class like `text-heading-xl` carries size, line height and weight together. |
| Fonts from the operating system | Geist and Inter self-hosted (latin subsets, WOFF2), cached for a year | No request to Google Fonts at page load. Spring Security marks every response `no-store`, so `WebConfig` gives `/fonts/**` its own `Cache-Control`. |
| Home was a welcome line | A Featured row with the four best-rated products in stock | The first page shows something to buy. Products without a rating sort last (`NULLS LAST`, which Postgres doesn't do by default for `DESC`). |
| Every button looked equally important (four filled buttons on each admin row) | Graded: one primary per page or row, then outline, grey and ghost buttons; destructive actions as red outlines | The obvious next action stands out, and Refund no longer sits level with Mark as shipped. |
| Order status as plain text ("PAID") | Colour badges: PENDING amber, PAID mint, SHIPPED ink, CANCELLED grey, REFUNDED red | Statuses can be scanned down a long list. |
| Dates like "15 10 2026" | "15 Oct 2026, 14:22" | The pattern was `dd MM yyyy`, which prints the month as a number. |
| The dashboard's all-time revenue in "$" | "€", like every other price | |
| Paging buttons disappeared on the first and last page | Shown disabled | The buttons stay in the same place from page to page. |
| Login and register fields with placeholders only | Visible labels, 44px fields, the password rule shown up front, a message under the field that's wrong | A placeholder disappears as soon as you type, and it isn't a reliable label for screen readers. |
| On phones, the main actions were somewhere down the page | The product page's Add To Cart and Buy, the cart's total and checkout button, and checkout's pay button are pinned to the bottom of the screen | They stay within thumb reach; the page gets bottom padding so the bar never covers content. |
| The navbar didn't show where you were | The current page's link is marked (`aria-current`), including Products on a product's details and edit pages and Admin on the order pages | `MainController` puts the request path in the model, because Thymeleaf 3.1 no longer lets templates read the request. |

</details>

<details>
<summary><strong>Seed data</strong>: 50 hand-made products → DummyJSON's 194</summary>

| Before | Now | Why |
| --- | --- | --- |
| 50 hand-written products in 9 types, 15 with a picture file in `static/images/` | DummyJSON's 194 products in 24 types, all with pictures, plus brand, rating, discount, gallery, tags and 582 reviews | A catalogue big enough to exercise the search, the filters, paging and the layout with long names and real pictures. |
| Seed SQL written by hand | `scripts/dummyjson-to-sql.mjs` writes `V3__dummyjson_products.sql` from DummyJSON's API | The output is committed, so builds and tests never depend on DummyJSON being up. Product ids are DummyJSON's, so new products continue after them. |

</details>

<details>
<summary><strong>Tests and tooling</strong>: 240 tests against a real PostgreSQL, Spotless formatting, migrations squashed</summary>

| Before | Now | Why |
| --- | --- | --- |
| No automated tests; every change was checked by clicking through the site | 240 tests in four layers, run by `mvn verify` (see [Testing](#testing)) | The payment flow has cases nobody clicks through by hand: duplicate webhooks, stock running out mid-payment, two refunds at once. |
| — | Tests run against a real PostgreSQL in Docker (Testcontainers), not an in-memory database | Row locks (`SELECT ... FOR UPDATE`), `CHECK` constraints and the Flyway migrations behave exactly as in the app. An in-memory stand-in would only imitate them. |
| Two refund clicks at the same moment could both put the stock back | `markRefunded` locks the order row, like `completePayment` | Found while writing the tests. Stripe refunded only once (same idempotency key), but the shelf ended up with stock that didn't exist. |
| No formatting rules | Spotless (palantir-java-format) fails the build on unformatted code | Style stops being a review topic. The one big formatting commit is listed in `.git-blame-ignore-revs`, so `git blame` skips it. |
| A migration for every change made along the way | Squashed into V1 (schema), V2 (accounts) and V3 (the DummyJSON catalogue) | Nothing had been deployed, so no database needed the step-by-step history. Three files are easier to read than many that undo each other. |

</details>

<!-- markdownlint-enable MD033 -->

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>Design notes</strong>: relations, transactions, locking, Stripe calls, refunds and the frontend</summary>

- **Links run from child to parent only** (`CartItem → User`, `OrderItem → Order`, `Review → Product`). A user's cart and orders, and a product's reviews, are loaded through repositories, not through collections on the parent.
- **`spring.jpa.open-in-view=false`:** the database session closes before the template renders. Each query states what it needs with `@EntityGraph`, so a template can't trigger extra hidden queries.
- **Stock is decremented in SQL** (`ProductRepository.decrementStock`), so the check and the update happen in one atomic step. Payments lock products in id order so two of them can't deadlock.
- **Stripe can deliver the same event twice, even at the same time.** `CartService.completePayment` locks the order row (`SELECT ... FOR UPDATE`) and only acts on a PENDING order, so a repeated event never takes stock twice. `markRefunded` does the same for refunds.
- **No database transaction stays open during a Stripe call.** The order is saved first, then the Checkout Session is created, then its id is saved. Refunds run after the stock transaction has rolled back.
- **Amounts go to Stripe in cents** with `movePointRight(2).longValueExact()`, with no floating-point step in between.
- **Admin refunds call Stripe first, then the database.** If the database step fails, the order still says PAID, and clicking Refund again is safe: the same idempotency key makes Stripe return the existing refund. The other order would risk an order marked REFUNDED whose customer never got their money.
- **Paging only fetches to-one relations.** The order list's `@EntityGraph` loads `user` but not `items`. Fetching a collection would make Hibernate page in memory, which loads every row again.
- **The stylesheet is build output.** The Tailwind CLI scans the templates and `code.js` and writes only the classes it finds to `target/classes/static/app.css`. A class name assembled at runtime would never be found, so templates always spell out full class names.
- **Shared looks are components, not copies.** Buttons, inputs, badges, chips, alerts, cards and tables are defined once in `app.css`'s `@layer components`, and repeated markup (product card, picture, filters, status badge) lives in Thymeleaf fragments, so the next redesign edits one place.
- **One URL, two answers for add to cart.** A plain form post redirects back to the products page. `code.js` sends `X-Requested-With: XMLHttpRequest`, which picks a second mapping that answers JSON with the new cart count (409 when there's no stock left). The header decides, not `Accept`, because browsers send `Accept: */*`.
- **The picture rule lives in two places.** A `CHECK` on `product.image` guards the data; `@Pattern` on the form records turns a bad value into a 400 or a form message instead of a database error (500).
- **Admin endpoints live under paths `SecurityConfig` already locks** (for example `POST /edit/{id}/active`). A new path would fall through to "any logged-in user".

</details>

<!-- markdownlint-enable MD033 -->

## Credits

- **Product data and pictures:** [DummyJSON](https://dummyjson.com), a free sample-data API. Pictures are loaded from its CDN.
- **Fonts:** [Geist](https://vercel.com/font) (Vercel) and [Inter](https://rsms.me/inter/) (Rasmus Andersson), both under the SIL Open Font License 1.1, downloaded from Google Fonts.
- **Icons:** [Heroicons](https://heroicons.com) v2.2.0 (MIT).

## To-Do

- Show the product data that's stored but not on any page yet: brand, rating, discount, the picture gallery, tags and reviews.
- CI (GitHub Actions running `mvn verify`) and automated dependency updates once the repository is public. Tailwind and `stripe-java` are pinned by hand until then.
- Content-hashed names for `app.css` and `code.js`, so they can be cached like the fonts.

## Known limitations

<!-- markdownlint-disable MD033 -- <details> dropdowns need HTML -->

<details>
<summary><strong>Payments and Stripe</strong>: duplicate orders, no stock reservation, card payments only</summary>

- Clicking "Pay with Stripe" twice, or in two tabs, creates two PENDING orders, and each could be paid.
- Stock isn't reserved while the customer is on Stripe's page. If it sells out in that window, the payment is refunded automatically and the order is cancelled.
- An unpaid order stays PENDING until Stripe expires its session (24 hours by default).
- Card payments only. Delayed methods such as bank debits (`checkout.session.async_payment_succeeded`) aren't handled.
- A refund made in the Stripe Dashboard doesn't update the order, because `charge.refunded` isn't handled. Refund from the app instead.
- The "View payment in Stripe" link points at the test-mode Dashboard.
- Locally, webhooks only arrive while `stripe listen` is running. Events missed while it's off aren't replayed.

</details>

<details>
<summary><strong>Shop and products</strong>: hot-linked pictures, discounts not charged, no cart +/−</summary>

- Product pictures are hot-linked from DummyJSON's CDN, so they need an internet connection, and the shop shows broken images if the CDN changes. Pictures are set as a path or URL; there's no upload.
- DummyJSON's discount is stored but neither shown nor charged: customers pay the full price.
- The cart has no +/− controls; items are added one at a time from the products page.
- Registering with a username longer than 50 characters shows a 500 error instead of a message: the length is checked by the database, not the form.
- The add-to-cart toast hides after 4 seconds, even while the pointer is on it.
- Error pages show no cart badge and no current-page mark, because they're rendered outside `MainController`.

</details>

<details>
<summary><strong>Admin</strong>: no refunds after shipping, the 320px navbar, 405 instead of 403</summary>

- SHIPPED orders can't be refunded; returns aren't modelled.
- "Last 7 days" on the dashboard counts orders by when they were placed, not when they were paid (there's no `paid_at` column).
- The admin's navbar scrolls sideways by a few pixels on a 320px-wide screen (customers' fits).
- When a customer tries a POST that's for admins only, the app shows 405 instead of 403: the access-denied page is GET-only, and Spring Security forwards the POST to it.

</details>

<details>
<summary><strong>Build</strong>: uncached stylesheet and script, an npm override</summary>

- `app.css` and `code.js` are sent with `Cache-Control: no-store`, so browsers download them on every page (fonts are cached).
- `package.json` overrides `@parcel/watcher` to 2.6.0 so `npm audit` is clean. Remove the override once a newer Tailwind CLI depends on it.

</details>

<!-- markdownlint-enable MD033 -->
