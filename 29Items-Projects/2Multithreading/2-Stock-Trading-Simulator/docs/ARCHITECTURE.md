# Stock Trading Simulator - Architecture

## 1. Pattern

The system is a layered modular monolith with application ports. One JVM is the correct deployment unit for a local concurrency simulator: it preserves deterministic, low-overhead measurements while keeping infrastructure replaceable.

~~~mermaid
flowchart TB
    CLI[CLI and 10K virtual clients] --> API[TradingSimulator application API]
    API --> OS[OrderService]
    API --> PS[PortfolioService]
    API --> PR[PriceService]
    API --> TS[TradeService]
    API --> FS[ProfitCalculationService]
    OS --> OB[InMemoryOrderBook - fair ReentrantLock]
    OS --> PF[ConcurrentPortfolioRepository - reservations and atomic settlement]
    OS --> TR[ConcurrentTradeRepository]
    OS --> AL[ConcurrentAuditLog]
    PR --> PC[StampedPriceCache - optimistic reads]
    PR --> MP[SyntheticPriceProvider]
    FS --> PF
    FS --> FJP[owned ForkJoinPool]
~~~

Dependencies point from presentation to application to domain/ports. Infrastructure implements ports and is assembled only by SimulatorApplication.

## 2. Component interactions

| Interaction | Implementation |
|---|---|
| Application calls | Direct typed calls to TradingSimulator; no network serialization is needed. |
| Commands | Synchronous domain mutation after asynchronous reference-price validation. |
| Order persistence | In-memory order map and per-symbol priority queues under one fair lock. |
| Portfolio persistence | Immutable Portfolio values in ConcurrentHashMap; one transaction lock protects reads, reservations, and two-account settlement. |
| Price retrieval | Optimistic cache read, coalesced async miss, injected provider, bounded timeout, and revision-based stale-fetch invalidation. |
| Trade/audit ledger | Concurrent append-only repositories returning sorted immutable snapshots. |
| Analytics | Recursive marked-profit reduction in a dedicated, owned ForkJoinPool. |

## 3. Order data flow

~~~mermaid
sequenceDiagram
    actor Trader
    participant VT as Virtual client thread
    participant API as TradingSimulator
    participant Price as PriceService
    participant Cache as StampedPriceCache
    participant Orders as OrderService
    participant Portfolios as PortfolioRepository
    participant Book as OrderBook
    participant Ledger as Trade/Audit ledgers

    Trader->>VT: submit validated command
    VT->>API: placeOrder(command)
    API->>Price: getPrice(symbol)
    Price->>Cache: optimistic read
    alt cache miss
        Price->>Price: coalesce and fetch with timeout
        Price->>Cache: write validated price
    end
    API->>API: validate configured price band
    API->>Orders: place(command)
    Orders->>Portfolios: reserve cash or shares
    Orders->>Book: submit under order-book lock
    Book->>Book: price/time match and partial fills
    Book-->>Orders: final order plus immutable trades
    loop every trade
        Orders->>Portfolios: atomically settle buyer and seller
    end
    Orders->>Ledger: append trades and lifecycle events
    Orders-->>VT: final order state
    VT-->>Trader: result or typed failure
~~~

Matching finishes before settlement, so slow work never occurs while the order-book lock is held. Pre-trade reservations make each returned trade settleable. Settlement has one transaction lock and therefore cannot expose a half-updated buyer/seller pair.

## 4. Matching rules and invariants

| Rule | Guarantee |
|---|---|
| Buy priority | Highest price, then earliest creation time, then lowest ID. |
| Sell priority | Lowest price, then earliest creation time, then lowest ID. |
| Execution price | Resting/older order's limit price. |
| Partial fills | remainingQuantity decreases exactly by trade quantity; status follows remaining quantity. |
| Quantity conservation | Sum of trade quantities never exceeds either order's original quantity. |
| Self-trade prevention | A crossing newly submitted order from the same trader is cancelled. |
| Buying power | Buy reservation uses limit price times remaining quantity. |
| Inventory | Sell reservation prevents total open sells from exceeding available shares. |
| Settlement | Cash notional and share quantity are transferred atomically and conserved. |

## 5. Concurrency and performance

| Concern | Primitive | Boundary |
|---|---|---|
| IDs | AtomicLong | Positive unique IDs with overflow detection. |
| Order book | fair ReentrantLock | Map and both priority queues mutate together. |
| Price cache | StampedLock | Validated optimistic read with read-lock fallback and counters. |
| Portfolios | ConcurrentHashMap plus transaction ReentrantLock | Transaction-consistent reads; atomic reservations and settlement. |
| Clients | virtual-thread executor | Configurable up to 1,000,000 blocking-style client tasks; verified at 10,000. |
| Pricing | CompletableFuture, concurrent in-flight map, and AtomicLong revisions | One request per symbol miss, bounded by timeout; explicit writes win races. |
| Profit | dedicated ForkJoinPool | CPU work is isolated from virtual-client scheduling. |

The test harness records elapsed and p95 order latency. scripts/profile.ps1 captures JFR data for allocation, lock, virtual-thread, and CPU inspection.

## 6. Errors, security, and logging

Domain validation throws IllegalArgumentException. Required null values fail immediately. Symbols, identifiers, money precision/scale, quantities, page sizes, profiles, timeouts, trader counts, and parallelism are bounded. Business failures use TradingException subclasses for insufficient funds, insufficient position, invalid order state, missing entity, and unavailable price. Asynchronous failures preserve their cause. Interrupted waits restore the interrupt flag and bounded simulation waits cancel unfinished work.

This local process has no authentication boundary or secrets. Inputs are validated at construction/application boundaries, list consumers can use bounded immutable pages, audit details exclude cash balances, and .env.example contains only non-secret configuration names. An HTTP adapter must authenticate before invoking the facade, enforce trader ownership, use TLS/rate limits/idempotency keys, and translate exceptions without exposing internals.

## 7. Optional external adapters

Persistence can be added by implementing PortfolioRepository, TradeRepository, OrderBook, and AuditLog; migrations must be introduced with that adapter because no relational schema exists in the current application. An HTTP or WebSocket adapter can invoke TradingSimulator without modifying domain services. A durable multi-JVM design would additionally need symbol ownership, idempotent command IDs, and a partitioned sequenced log.

Docker remains non-applicable to the current local JAR. Creating an unused container or database would add an untested deployment contract and contradict the chosen target.
