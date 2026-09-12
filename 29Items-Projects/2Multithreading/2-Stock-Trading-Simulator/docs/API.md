# Java Application API

## Boundary

The simulator is a local Java SE application, so its API is the typed `TradingSimulator` facade rather than an HTTP/OpenAPI surface. `SimulatorApplication` is the production composition root. Embedded callers may compose the same application services with alternate implementations of the ports in `application.port`.

## Operations

| Method | Result | Behavior |
|---|---|---|
| `placeOrder(PlaceOrderCommand)` | `CompletableFuture<Order>` | Fetches reference price, validates the price band, reserves funds/position, matches, settles, and audits. |
| `replaceOrder(long, PlaceOrderCommand)` | `CompletableFuture<Order>` | Validates ownership/state and price before cancelling and resubmitting. |
| `getOrder(long)` | `Optional<Order>` | Returns an order by positive ID. |
| `listOrders()` | `List<Order>` | Returns an immutable ID-sorted snapshot. |
| `listOrders(int, int)` | `Page<Order>` | Returns an immutable offset page. |
| `cancelOrder(long)` | `Optional<Order>` | Cancels an active order and releases its remaining reservation. |
| `createPortfolio(Portfolio)` | `Portfolio` | Creates a unique trader portfolio. |
| `updatePortfolio(Portfolio)` | `Portfolio` | Replaces a portfolio when it has no active reservation. |
| `getPortfolio(String)` | `Optional<Portfolio>` | Returns a transaction-consistent snapshot. |
| `listPortfolios()` | `List<Portfolio>` | Returns a transaction-consistent immutable snapshot. |
| `listPortfolios(int, int)` | `Page<Portfolio>` | Returns an immutable offset page. |
| `deletePortfolio(String)` | `Portfolio` | Deletes an existing unreserved portfolio. |
| `getPrice(String)` | `CompletableFuture<BigDecimal>` | Returns a cached value or a coalesced, timed provider fetch. |
| `updatePrice(String, BigDecimal)` | `BigDecimal` | Validates and stores a price, invalidating an older fetch. |
| `deletePrice(String)` | `Optional<BigDecimal>` | Removes a price and invalidates an older fetch. |
| `listPrices()` | `Map<String, BigDecimal>` | Returns an immutable cache snapshot. |
| `getTrade(long)` | `Optional<Trade>` | Returns an immutable trade by positive ID. |
| `listTrades()` | `List<Trade>` | Returns immutable append-only trade history. |
| `listTrades(int, int)` | `Page<Trade>` | Returns an immutable offset page. |
| `listAuditEvents()` | `List<AuditEvent>` | Returns sequence-ordered audit history. |
| `listAuditEvents(int, int)` | `Page<AuditEvent>` | Returns an immutable offset page. |
| `calculateAggregateProfit(Map, BigDecimal)` | `BigDecimal` | Calculates marked aggregate profit in the owned ForkJoinPool. |

For every paged method, `offset` is between zero and the current result size and `limit` is between 1 and 1,000. `Page` returns `items`, `offset`, `limit`, `totalElements`, and `hasNext`.

## Command example

~~~java
PlaceOrderCommand command = new PlaceOrderCommand(
        "trader-42",
        "aapl",
        OrderSide.BUY,
        10,
        new BigDecimal("224.50"));

Order result = simulator.placeOrder(command).join();
Page<Order> firstPage = simulator.listOrders(0, 100);
~~~

Symbols are normalized with `Locale.ROOT` to uppercase. Symbols must start with a letter and contain 1-15 letters, digits, dots, or hyphens. Trader IDs must contain 1-64 letters, digits, dots, underscores, or hyphens and start with an alphanumeric character. Quantity is 1-1,000,000,000. Money is exact `BigDecimal`, at most 24 digits and 4 decimal places.

## Error model

| Failure | Java representation |
|---|---|
| Null, malformed, out-of-range input | `NullPointerException` for missing required values or `IllegalArgumentException` for invalid values |
| Missing portfolio | `EntityNotFoundException` |
| Insufficient cash | `InsufficientFundsException` |
| Insufficient shares | `InsufficientPositionException` |
| Invalid order lifecycle/ownership | `OrderStateException` |
| Provider failure | `PriceUnavailableException` wrapped by asynchronous completion |
| Price timeout | Exceptional `CompletableFuture` with timeout cause |

Async callers should handle completion explicitly:

~~~java
simulator.getPrice("AAPL")
        .thenAccept(price -> System.out.println(price.toPlainString()))
        .exceptionally(failure -> {
            logger.log(System.Logger.Level.ERROR, "Price request failed", failure);
            return null;
        });
~~~

The example logger should be application-owned; the simulator never prints internal exceptions or secrets to the returned data model.

