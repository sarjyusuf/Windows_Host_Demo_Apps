# E-Commerce Microservices Platform

A production-grade distributed .NET 8 microservices application that simulates a complete online store with an asynchronous order fulfillment pipeline. The platform consists of **5 independent .NET processes** — 3 ASP.NET Core Kestrel HTTP APIs and 2 long-running Worker Services registered with the Windows Service Control Manager — communicating through synchronous HTTP calls and asynchronous file-based message queues, backed by 4 independent SQLite databases.

---

## Table of Contents

- [Application Overview](#application-overview)
- [Why This Application Is Complex](#why-this-application-is-complex)
- [Architecture](#architecture)
- [Technology Stack](#technology-stack)
- [Services in Detail](#services-in-detail)
- [Shared Library](#shared-library)
- [Cross-Service Communication](#cross-service-communication)
- [Data Layer](#data-layer)
- [Asynchronous Messaging System](#asynchronous-messaging-system)
- [Order Lifecycle State Machine](#order-lifecycle-state-machine)
- [End-to-End Request Flow](#end-to-end-request-flow)
- [Frontend UI](#frontend-ui)
- [Windows Deployment](#windows-deployment)
- [API Reference](#api-reference)
- [Running the Application](#running-the-application)

---

## Application Overview

This platform models a realistic e-commerce system where a customer browses products, adds items to a shopping cart, and places an order. The order then flows through an asynchronous processing pipeline: inventory is reserved transactionally, the order is marked as fulfilled, and the customer receives a notification. This entire flow spans 5 separate OS processes, 7 cross-service HTTP calls, 2 asynchronous message queue hops, and writes to 3 separate databases — all for a single checkout.

The application runs entirely on Windows. The three HTTP APIs run as self-hosted Kestrel processes. The two background workers run as Windows Services managed by the Service Control Manager (SCM), with automatic restart on failure and delayed auto-start. No IIS, no reverse proxy, no external message broker, no external database server — every dependency is embedded within the .NET processes themselves.

---

## Why This Application Is Complex

This is not a single-process monolith or a basic CRUD API. It is a **distributed system** composed of 5 independently deployed and independently running .NET processes, each with its own memory space, its own database, its own lifecycle, and its own failure domain. The complexity is structural and architectural:

### 1. Five Independent OS Processes

Each service compiles to its own executable and runs as a separate Windows process with its own PID. There is no shared memory between them. They discover each other only through network calls and filesystem-based queues. The process types are:

| Process | SDK | Hosting Model | Port |
|---------|-----|---------------|------|
| WebStorefront | `Microsoft.NET.Sdk.Web` | ASP.NET Core Kestrel (self-hosted) | 5100 |
| OrderApi | `Microsoft.NET.Sdk.Web` | ASP.NET Core Kestrel (self-hosted) | 5101 |
| InventoryApi | `Microsoft.NET.Sdk.Web` | ASP.NET Core Kestrel (self-hosted) | 5102 |
| OrderProcessor | `Microsoft.NET.Sdk.Worker` | Windows Service (SCM-managed) | — |
| NotificationWorker | `Microsoft.NET.Sdk.Worker` | Windows Service (SCM-managed) | — |

### 2. Two Distinct Process Hosting Models

The web APIs use `Microsoft.NET.Sdk.Web` and self-host via Kestrel bound to `0.0.0.0` on their respective ports. They are launched as background processes with stdout/stderr redirected to log files.

The workers use `Microsoft.NET.Sdk.Worker` with `Microsoft.Extensions.Hosting.WindowsServices`. They call `UseWindowsService()` in their host builder, which integrates with the Windows SCM. They are registered via `sc.exe create` with `start= delayed-auto`, meaning Windows starts them automatically after boot with a short delay. Recovery is configured via `sc.exe failure` to restart automatically after 5, 10, and 30 seconds on successive failures.

### 3. Asynchronous Event-Driven Pipeline

A checkout does not complete synchronously. The WebStorefront calls the OrderApi to create the order, which persists it and publishes an `OrderEvent` message to a filesystem-based queue. A completely separate process — the OrderProcessor Windows Service — polls this queue every 3 seconds, picks up the event, orchestrates a multi-step fulfillment workflow (status updates, inventory reservation), and publishes a `FulfillmentEvent` to a second queue. A third process — the NotificationWorker Windows Service — polls that second queue every 5 seconds and generates customer notifications.

A single checkout request triggers a chain that crosses **4 service boundaries** and **2 asynchronous messaging boundaries** before reaching completion.

### 4. Distributed Trace Context Propagation

Every synchronous HTTP call propagates W3C Trace Context headers (`traceparent` and `tracestate`). When the OrderApi publishes a message to the file queue, it embeds the trace context from the incoming HTTP request into the `QueueMessage<T>` envelope. When the OrderProcessor dequeues the message, it extracts the trace context and attaches it to all of its outbound HTTP calls to OrderApi and InventoryApi. The fulfillment event also carries the trace context forward to the NotificationWorker.

This means a single distributed trace can span all 5 services across both synchronous (HTTP) and asynchronous (file queue) boundaries.

### 5. Four Independent Databases

Each service that needs persistent storage has its own SQLite database:

| Database | Owner | Tables | Purpose |
|----------|-------|--------|---------|
| `storefront.db` | WebStorefront | `Products` | Product catalog (10 seeded items) |
| `orders.db` | OrderApi | `Orders`, `OrderItems` | Order records with line items |
| `inventory.db` | InventoryApi | `InventoryItems` | Stock levels per product (10 seeded items) |
| `notifications.db` | NotificationWorker | `Notifications` | Email notification records |

There is no shared database. Services communicate exclusively through HTTP and message queues.

### 6. Transactional Inventory Management

The InventoryApi's `/api/inventory/reserve` endpoint performs atomic multi-line inventory reservation inside an explicit EF Core database transaction (`BeginTransactionAsync`). For each line in the reservation request, it loads the inventory record, validates that `QuantityAvailable` (computed as `QuantityOnHand - QuantityReserved`) is sufficient, and increments `QuantityReserved`. If any line fails validation, the entire transaction is rolled back — no partial reservations. Concurrent reservation requests are serialized at the database level.

### 7. Multi-Step Order State Machine

An order transitions through 8 possible states, with transitions driven by different services:

```
Pending → PaymentValidated → Processing → InventoryReserved → Fulfilled → Shipped
                                ↓
                              Failed
                                ↓
                            Cancelled
```

The `OrderApi` sets `Pending` → `PaymentValidated` on creation. The `OrderProcessor` drives `Processing` → `InventoryReserved` → `Fulfilled` (or `Failed`). Each transition is an HTTP PUT call from the OrderProcessor to the OrderApi, meaning the state machine is distributed across two separate processes.

### 8. Failure Handling and Retry Logic

The OrderProcessor moves failed messages to a `failed/` directory and publishes a failure `FulfillmentEvent` so the NotificationWorker can inform the customer. The NotificationWorker has its own retry mechanism: every ~30 seconds (every 6th polling loop), it queries its database for any notifications stuck in `Pending` status and re-attempts sending them. Failed notifications are marked as `Failed` after retry exhaustion.

### 9. Dependency Injection Complexity

The OrderProcessor uses **keyed dependency injection** (`AddKeyedSingleton` with `[FromKeyedServices]` attribute) to inject two separate `FileMessageQueue` instances — one for reading `order-events` and one for writing `fulfillment-events`. It also uses **named HTTP clients** via `IHttpClientFactory` to maintain separate connection pools and configurations for the OrderApi and InventoryApi. This exercises modern .NET 8 DI patterns that are uncommon in simple applications.

---

## Architecture

```
                                    ┌─────────────────┐
                              ┌────>│   OrderApi       │──── SQLite (orders.db)
                              │     │   :5101          │
                              │     └────────┬─────────┘
                              │              │
   ┌──────────────────┐       │     Publishes OrderEvent to
   │  WebStorefront    │───────┤     file-based message queue
   │  :5100            │       │              │
   │                   │       │              ▼
   │  Product catalog  │       │     ┌─────────────────────────────────┐
   │  Shopping cart     │       │     │  order-events queue (filesystem) │
   │  Checkout flow    │       │     │  pending/ → processing/ →       │
   │  Frontend UI      │       │     │  completed/ | failed/            │
   └──────────────────┘       │     └──────────────┬──────────────────┘
                              │                    │
                              │                    ▼
                              │     ┌─────────────────────────┐
                              │     │  OrderProcessor          │
                              │     │  (Windows Service)       │
                              │     │                          │
                              │     │  Polls queue every 3s    │
                              │     │  Updates order status    │───── HTTP to OrderApi
                              │     │  Reserves inventory      │───── HTTP to InventoryApi
                              │     │  Publishes fulfillment   │
                              │     └──────────┬──────────────┘
                              │                │
                              │                ▼
                              │     ┌─────────────────────────────────────┐
                              │     │  fulfillment-events queue (filesystem)│
                              │     └──────────────┬──────────────────────┘
                              │                    │
                              │                    ▼
                              │     ┌──────────────────────────┐
                              │     │  NotificationWorker       │
                              │     │  (Windows Service)        │
                              │     │                           │
                              │     │  Polls queue every 5s     │──── SQLite (notifications.db)
                              │     │  Sends email simulation   │
                              │     │  Retries every ~30s       │
                              │     └──────────────────────────┘
                              │
                              └────>┌─────────────────┐
                                    │  InventoryApi    │──── SQLite (inventory.db)
                                    │  :5102           │
                                    └─────────────────┘
```

---

## Technology Stack

### Runtime and Frameworks

| Component | Technology | Version | NuGet Package |
|-----------|------------|---------|---------------|
| **Runtime** | .NET | **8.0 LTS** | — |
| **Web Framework** | ASP.NET Core with Kestrel | 8.0 | `Microsoft.NET.Sdk.Web` (SDK) |
| **Worker Framework** | .NET Generic Host | 8.0 | `Microsoft.NET.Sdk.Worker` (SDK) |
| **Windows Service Integration** | WindowsServices hosting extension | 8.0.0 | `Microsoft.Extensions.Hosting.WindowsServices` |
| **ORM** | Entity Framework Core | 8.0.0 | `Microsoft.EntityFrameworkCore.Sqlite` |
| **Database Engine** | SQLite (embedded, serverless) | 3.x (via EF Core) | `Microsoft.EntityFrameworkCore.Sqlite` |
| **HTTP Client Factory** | Microsoft.Extensions.Http | 8.0.0 | `Microsoft.Extensions.Http` |
| **Logging Abstractions** | Microsoft.Extensions.Logging | 8.0.0 | `Microsoft.Extensions.Logging.Abstractions` |

### Built-in .NET 8 Features Used

| Feature | Namespace / API | Used By |
|---------|-----------------|---------|
| **IMemoryCache** | `Microsoft.Extensions.Caching.Memory` | WebStorefront (shopping cart session storage with 4-hour sliding expiration) |
| **IHttpClientFactory** with named clients | `Microsoft.Extensions.Http` | WebStorefront, OrderProcessor (separate named clients for OrderApi and InventoryApi) |
| **Keyed DI Services** (.NET 8+) | `Microsoft.Extensions.DependencyInjection` | OrderProcessor (`AddKeyedSingleton` + `[FromKeyedServices]` for two FileMessageQueue instances) |
| **System.Text.Json** | `System.Text.Json` | All services (JSON serialization/deserialization) |
| **JsonStringEnumConverter** | `System.Text.Json.Serialization` | OrderApi, OrderProcessor (serialize `OrderStatus` enum as strings across service boundaries) |
| **BackgroundService** | `Microsoft.Extensions.Hosting` | OrderProcessor, NotificationWorker (long-running polling loops) |
| **UseWindowsService()** | `Microsoft.Extensions.Hosting.WindowsServices` | OrderProcessor, NotificationWorker (SCM integration) |
| **EF Core Transactions** | `Microsoft.EntityFrameworkCore` | InventoryApi (`BeginTransactionAsync` / `CommitAsync` / `RollbackAsync` for atomic reservation) |
| **EF Core Seed Data** | `ModelBuilder.HasData()` | WebStorefront (10 products), InventoryApi (10 inventory items) |
| **CORS Middleware** | `Microsoft.AspNetCore.Cors` | OrderApi, InventoryApi (cross-origin access from frontend UI) |
| **Static File Middleware** | `Microsoft.AspNetCore.StaticFiles` | WebStorefront (serves frontend SPA from `wwwroot/`) |

### Project SDK Breakdown

| Project | SDK | Output | Target Framework |
|---------|-----|--------|-----------------|
| `Shared.csproj` | `Microsoft.NET.Sdk` | Class Library (.dll) | `net8.0` |
| `WebStorefront.csproj` | `Microsoft.NET.Sdk.Web` | ASP.NET Core Executable | `net8.0` |
| `OrderApi.csproj` | `Microsoft.NET.Sdk.Web` | ASP.NET Core Executable | `net8.0` |
| `InventoryApi.csproj` | `Microsoft.NET.Sdk.Web` | ASP.NET Core Executable | `net8.0` |
| `OrderProcessor.csproj` | `Microsoft.NET.Sdk.Worker` | Worker Service Executable | `net8.0` |
| `NotificationWorker.csproj` | `Microsoft.NET.Sdk.Worker` | Worker Service Executable | `net8.0` |

### Deployment and Infrastructure Tools

| Tool | Purpose |
|------|---------|
| `dotnet publish -c Release -r win-x64 --self-contained false` | Framework-dependent publish for Windows x64 |
| `sc.exe create` | Register .NET Worker Services with Windows Service Control Manager |
| `sc.exe failure` | Configure automatic restart on service failure (5s/10s/30s) |
| `Start-Process` (PowerShell) | Launch web API executables as background processes with log redirection |
| `netsh advfirewall` | Open Windows Firewall ports for external access |
| PowerShell 5.1+ | All deployment and management scripts |

---

## Services in Detail

### 1. WebStorefront — Customer-Facing Gateway

| Property | Value |
|----------|-------|
| **SDK** | `Microsoft.NET.Sdk.Web` |
| **Port** | 5100 |
| **Database** | `storefront.db` (SQLite) — 10 seeded products |
| **Caching** | `IMemoryCache` — shopping carts with 4-hour sliding expiration |
| **HTTP Clients** | Named clients: `OrderApi` (localhost:5101), `InventoryApi` (localhost:5102) |
| **Static Files** | `wwwroot/index.html` — single-page frontend application |

The WebStorefront is the entry point for customers. It serves a single-page HTML/CSS/JS frontend, exposes a RESTful product catalog API backed by SQLite with 10 seeded products across 5 categories (Electronics, Clothing, Home & Kitchen, Sports & Fitness, Accessories), manages shopping carts in-memory using `IMemoryCache` with session-based keys, and orchestrates the checkout flow.

During checkout, the WebStorefront performs a **two-phase process**:
1. **Inventory validation** — For each cart item, it calls `GET /api/inventory/{productId}` on the InventoryApi to verify stock availability before committing the order.
2. **Order creation** — If all items are available, it POSTs the order to the OrderApi, which persists it and publishes an asynchronous event.

All outbound HTTP calls include W3C Trace Context header forwarding via the `ForwardTraceHeaders()` method, which extracts `traceparent` and `tracestate` from the incoming request and attaches them to the outgoing `HttpClient`.

### 2. OrderApi — Order Management and Event Publishing

| Property | Value |
|----------|-------|
| **SDK** | `Microsoft.NET.Sdk.Web` |
| **Port** | 5101 |
| **Database** | `orders.db` (SQLite) — `Orders` and `OrderItems` tables with foreign key relationships |
| **Message Queue** | `FileMessageQueue` singleton — publishes to `order-events/pending/` |
| **JSON** | `JsonStringEnumConverter` on controller options — serializes `OrderStatus` as strings |

The OrderApi manages the full order lifecycle. When a new order is created via POST:

1. Generates a unique order number (`ORD-{unix_timestamp_ms}`)
2. Persists the order and line items to SQLite with status `Pending`
3. Computes `TotalAmount` from line item quantities and unit prices
4. Extracts `traceparent`/`tracestate` headers from the incoming HTTP request
5. Creates a `QueueMessage<OrderEvent>` envelope with the order data and trace context
6. Writes the message as a timestamped JSON file to `order-events/pending/`

The OrderApi also exposes status update endpoints used by the OrderProcessor to drive the order state machine. Each status update sets timestamp fields (`ProcessedAt`, `FulfilledAt`) based on the transition.

### 3. InventoryApi — Stock Management with Transactional Reservations

| Property | Value |
|----------|-------|
| **SDK** | `Microsoft.NET.Sdk.Web` |
| **Port** | 5102 |
| **Database** | `inventory.db` (SQLite) — 10 seeded inventory items with warehouse locations |
| **Transactions** | Explicit EF Core `BeginTransactionAsync` for atomic multi-line reservations |

The InventoryApi tracks stock levels for all 10 products with initial quantities of 50-200 units across warehouse locations (A-1 through C-2). Each `InventoryItem` has `QuantityOnHand`, `QuantityReserved`, and a computed `QuantityAvailable` property.

The reservation endpoint (`POST /api/inventory/reserve`) accepts a `ReservationRequest` containing multiple `ReservationLine` items. It processes all lines within a single database transaction:

```
BeginTransaction
  → For each line: Load inventory → Validate availability → Increment QuantityReserved
  → If any line fails: RollbackTransaction (no partial reservations)
  → If all succeed: SaveChanges → CommitTransaction
```

A matching release endpoint (`POST /api/inventory/release`) reverses reservations for cancelled orders using the same transactional pattern.

### 4. OrderProcessor — Asynchronous Order Fulfillment Engine

| Property | Value |
|----------|-------|
| **SDK** | `Microsoft.NET.Sdk.Worker` |
| **Hosting** | Windows Service via `UseWindowsService()` |
| **Service Name** | `EcommerceOrderProcessor` |
| **Startup Type** | Automatic (Delayed Start) |
| **Recovery** | Auto-restart on failure: 5s → 10s → 30s |
| **Polling Interval** | Every 3 seconds |
| **DI Pattern** | Keyed singletons (`[FromKeyedServices("order-events")]`, `[FromKeyedServices("fulfillment-events")]`) |
| **HTTP Clients** | Named clients: `OrderApi`, `InventoryApi` via `IHttpClientFactory` |
| **NuGet Dependency** | `Microsoft.Extensions.Http` 8.0.0 (required for `IHttpClientFactory` in Worker SDK — not included by default unlike the Web SDK) |

The OrderProcessor is the core orchestration engine. It runs as a Windows Service under LocalSystem, polling the `order-events` queue every 3 seconds. When it dequeues an `OrderEvent`, it executes a 6-step fulfillment pipeline:

1. **Update status → Processing** — HTTP PUT to OrderApi
2. **Fetch full order** — HTTP GET to OrderApi (by order number) to retrieve line items
3. **Build reservation request** — Maps `OrderItem` list to `ReservationRequest` with `ReservationLine` per item
4. **Reserve inventory** — HTTP POST to InventoryApi's transactional reservation endpoint
5. **Update status → InventoryReserved → Fulfilled** — Two HTTP PUT calls to OrderApi
6. **Publish FulfillmentEvent** — Writes success event to `fulfillment-events/pending/`

If any step fails, the OrderProcessor:
- Updates the order status to `Failed` via the OrderApi
- Publishes a failure `FulfillmentEvent` with the error reason
- Moves the queue message to `failed/`

All outbound HTTP calls carry the trace context extracted from the dequeued message, maintaining the distributed trace across the async boundary.

### 5. NotificationWorker — Customer Notification Service

| Property | Value |
|----------|-------|
| **SDK** | `Microsoft.NET.Sdk.Worker` |
| **Hosting** | Windows Service via `UseWindowsService()` |
| **Service Name** | `EcommerceNotificationWorker` |
| **Startup Type** | Automatic (Delayed Start) |
| **Recovery** | Auto-restart on failure: 5s → 10s → 30s |
| **Polling Interval** | Every 5 seconds |
| **Retry Interval** | Every ~30 seconds (every 6th loop) |
| **Database** | `notifications.db` (SQLite) — `Notifications` table |

The NotificationWorker watches the `fulfillment-events` queue and generates customer notifications. For each event:

1. Dequeues the `FulfillmentEvent` message
2. Creates a `Notification` record in SQLite with contextual email content:
   - **Success**: "Your order {number} has been fulfilled!" with shipping confirmation language
   - **Failure**: "Issue with your order {number}" with the failure reason
3. Simulates email sending (500ms delay representing SMTP latency)
4. Updates notification status to `Sent` with timestamp
5. Marks the queue message as completed

The worker also runs a **retry loop** every ~30 seconds that queries for any `Notification` records stuck in `Pending` status (e.g., from a previous crash before send completion) and re-attempts delivery, up to marking them as `Failed`.

---

## Shared Library

The `Shared` project (`EcommercePlatform.Shared`, SDK: `Microsoft.NET.Sdk`, target: `net8.0`) is a class library referenced by all 5 services. It contains:

### Domain Models (7 model classes, 3 enums)

- **`Product`** — Id, Name, Description, Category, Price, Sku
- **`Order`** / **`OrderItem`** — Full order aggregate with 8-state `OrderStatus` enum (Pending, PaymentValidated, Processing, InventoryReserved, Fulfilled, Shipped, Failed, Cancelled). `OrderItem` has a computed `LineTotal` property.
- **`InventoryItem`** — QuantityOnHand, QuantityReserved, computed `QuantityAvailable`, WarehouseLocation
- **`ReservationRequest`** / **`ReservationLine`** / **`ReservationResult`** / **`ReservationConfirmation`** — DTOs for the transactional inventory reservation workflow
- **`CartItem`** / **`ShoppingCart`** / **`CheckoutRequest`** — Shopping cart models with computed `Total`
- **`Notification`** — NotificationType (OrderFulfilled, OrderFailed, ShippingUpdate), NotificationStatus (Pending, Sent, Failed)

### Messaging Infrastructure

- **`QueueMessage<T>`** — Generic message envelope carrying `MessageId` (GUID), `MessageType` (typeof(T).Name), `Payload`, `EnqueuedAt` (UTC timestamp), `TraceParent`, `TraceState`, and extensible `Headers` dictionary
- **`OrderEvent`** — Published by OrderApi on order creation: OrderNumber, EventType, OrderId, CustomerEmail, CustomerName, TotalAmount, Timestamp
- **`FulfillmentEvent`** — Published by OrderProcessor after processing: OrderNumber, OrderId, CustomerEmail, CustomerName, Success (bool), FailureReason (nullable), Timestamp
- **`FileMessageQueue`** — Full implementation described in [Asynchronous Messaging System](#asynchronous-messaging-system)

### ServiceDefaults (SSI Diagnostics)

The `ServiceDefaults.LogDatadogConfig()` static method is called by every service on startup. It reads and logs the following environment variables to stdout:

- `DD_SERVICE`, `DD_ENV`, `DD_VERSION` — Datadog unified service tags
- `DD_TRACE_ENABLED`, `DD_AGENT_HOST` — Trace agent configuration
- `COR_PROFILER`, `CORECLR_PROFILER` — .NET CLR profiler GUIDs (set by SSI injection)
- `Environment.ProcessId`, `Environment.ProcessPath` — Process identity

---

## Cross-Service Communication

### Synchronous HTTP (IHttpClientFactory)

All cross-service HTTP calls use `IHttpClientFactory` with named clients. Each named client has a configured `BaseAddress`, `Accept` header, and 30-second timeout. The factory manages `HttpMessageHandler` lifetimes and connection pooling automatically.

| Caller | Target | Named Client | Calls |
|--------|--------|-------------|-------|
| WebStorefront | OrderApi :5101 | `"OrderApi"` | `POST /api/orders`, inventory checks |
| WebStorefront | InventoryApi :5102 | `"InventoryApi"` | `GET /api/inventory/{productId}` |
| OrderProcessor | OrderApi :5101 | `"OrderApi"` | `PUT /api/orders/{id}/status`, `GET /api/orders/by-number/{num}` |
| OrderProcessor | InventoryApi :5102 | `"InventoryApi"` | `POST /api/inventory/reserve` |

### W3C Trace Context Propagation

Every HTTP request propagates `traceparent` and `tracestate` headers:

- **WebStorefront** → extracts from incoming browser request, forwards via `ForwardTraceHeaders()` to `HttpClient.DefaultRequestHeaders`
- **OrderApi** → extracts from incoming request, embeds into `QueueMessage<OrderEvent>.TraceParent`/`.TraceState`
- **OrderProcessor** → extracts from dequeued `QueueMessage`, attaches to outbound `HttpRequestMessage.Headers` via `AddTraceContext()`
- **OrderProcessor** → embeds into `QueueMessage<FulfillmentEvent>` for NotificationWorker

This creates a continuous trace across: Browser → WebStorefront → OrderApi → [queue] → OrderProcessor → InventoryApi → [queue] → NotificationWorker.

### Asynchronous File Queue

See [Asynchronous Messaging System](#asynchronous-messaging-system).

---

## Data Layer

### Entity Framework Core Configuration

All database access uses **Entity Framework Core 8.0.0 with the SQLite provider**. Each service has its own `DbContext` subclass with Fluent API configuration in `OnModelCreating()`:

- **`StorefrontDbContext`** — `Products` table with max-length constraints, decimal precision (`decimal(18,2)` for Price), unique SKU. Seeds 10 products via `HasData()`.
- **`OrderDbContext`** — `Orders` and `OrderItems` tables with foreign key relationship (`Order.Items`), unique index on `OrderNumber`. Enum-to-string conversion for `OrderStatus`.
- **`InventoryDbContext`** — `InventoryItems` table with `ProductId` index. Seeds 10 items with quantities 50-200 and warehouse locations. Ignores computed `QuantityAvailable` property.
- **`NotificationDbContext`** — `Notifications` table with enum-to-string conversions for both `NotificationType` and `NotificationStatus`.

All databases are created automatically on first startup via `Database.EnsureCreated()`. No migrations are used — the schema is always created fresh from the model if the database file doesn't exist.

### Seed Data

The WebStorefront seeds **10 products** across 5 categories:

| ID | Product | Category | Price | SKU |
|----|---------|----------|-------|-----|
| 1 | Wireless Bluetooth Headphones | Electronics | $89.99 | ELEC-WBH-001 |
| 2 | USB-C Laptop Charger 65W | Electronics | $34.99 | ELEC-ULC-002 |
| 3 | Mechanical Gaming Keyboard | Electronics | $129.99 | ELEC-MGK-003 |
| 4 | Men's Classic Fit T-Shirt | Clothing | $19.99 | CLTH-MCT-004 |
| 5 | Women's Running Shoes | Clothing | $74.99 | CLTH-WRS-005 |
| 6 | Stainless Steel Water Bottle | Home & Kitchen | $24.99 | HOME-SSW-006 |
| 7 | Portable Bluetooth Speaker | Electronics | $49.99 | ELEC-PBS-007 |
| 8 | Yoga Mat Premium | Sports & Fitness | $29.99 | SPRT-YMP-008 |
| 9 | Leather Wallet Bifold | Accessories | $39.99 | ACCS-LWB-009 |
| 10 | LED Desk Lamp | Home & Kitchen | $44.99 | HOME-LDL-010 |

The InventoryApi seeds matching inventory records with initial stock of 50-200 units per product across warehouse locations A-1 through C-2.

---

## Asynchronous Messaging System

The `FileMessageQueue` is a custom messaging implementation that uses the filesystem as a message broker. No RabbitMQ, Redis, Kafka, or any external broker is required.

### Directory Structure

```
C:\DemoApps\ecommerce\queues\
├── order-events\
│   ├── pending\        ← Producer writes new messages here
│   ├── processing\     ← Consumer atomically moves message here on dequeue
│   ├── completed\      ← Consumer moves here on successful processing
│   └── failed\         ← Consumer moves here on processing failure
└── fulfillment-events\
    ├── pending\
    ├── processing\
    ├── completed\
    └── failed\
```

### Message Format

Each message is a JSON file named `{yyyyMMddHHmmssfff}_{messageId}.json`:

```json
{
  "messageId": "a1b2c3d4e5f6...",
  "messageType": "OrderEvent",
  "payload": {
    "orderNumber": "ORD-1740178234567",
    "eventType": "OrderCreated",
    "orderId": 1,
    "customerEmail": "user@example.com",
    "customerName": "Jane Doe",
    "totalAmount": 89.99,
    "timestamp": "2026-02-21T15:30:34Z"
  },
  "enqueuedAt": "2026-02-21T15:30:34Z",
  "traceParent": "00-abcdef1234567890-fedcba0987654321-01",
  "traceState": "dd=s:1;t.dm:-0",
  "headers": {}
}
```

### Operations

| Operation | Implementation | Concurrency Guarantee |
|-----------|---------------|----------------------|
| **Enqueue** | `File.WriteAllTextAsync()` to `pending/{timestamp}_{id}.json` | Timestamped filenames prevent collisions |
| **Dequeue** | `File.Move()` from `pending/` to `processing/` | Atomic OS filesystem operation — only one consumer succeeds, others get `IOException` and retry |
| **Complete** | `File.Move()` from `processing/` to `completed/` | Single consumer owns the file |
| **Fail** | `File.Move()` from `processing/` to `failed/` | Single consumer owns the file |

The dequeue operation scans `pending/` for `*.json` files sorted by filename (ensuring FIFO ordering via the timestamp prefix), then attempts an atomic `File.Move()` to the `processing/` directory. This is the concurrency control mechanism — if two consumers race to dequeue the same file, only one move succeeds.

---

## Order Lifecycle State Machine

```
                           OrderApi creates order
                                    │
                                    ▼
                              ┌──────────┐
                              │  Pending  │
                              └─────┬────┘
                                    │ OrderApi validates payment
                                    ▼
                         ┌───────────────────┐
                         │ PaymentValidated   │ ← OrderEvent published to queue
                         └─────────┬─────────┘
                                   │ OrderProcessor dequeues
                                   ▼
                           ┌──────────────┐
                    ┌──────│  Processing   │──────┐
                    │      └──────────────┘      │
                    │ success                     │ failure
                    ▼                             ▼
         ┌───────────────────┐            ┌──────────┐
         │ InventoryReserved  │            │  Failed   │
         └─────────┬─────────┘            └──────────┘
                   │
                   ▼
             ┌───────────┐
             │ Fulfilled  │ ← FulfillmentEvent published to queue
             └─────┬─────┘
                   │ (future: shipping integration)
                   ▼
             ┌───────────┐
             │  Shipped   │
             └───────────┘
```

**Services responsible for each transition:**

| Transition | Service | Mechanism |
|-----------|---------|-----------|
| → Pending | OrderApi | On order creation |
| Pending → PaymentValidated | OrderApi | Inline during creation (simulated) |
| PaymentValidated → Processing | OrderProcessor | HTTP PUT after dequeue |
| Processing → InventoryReserved | OrderProcessor | HTTP PUT after successful reservation |
| InventoryReserved → Fulfilled | OrderProcessor | HTTP PUT after reservation confirmation |
| Processing → Failed | OrderProcessor | HTTP PUT on reservation failure or error |

---

## End-to-End Request Flow

A single checkout triggers the following sequence across all 5 services:

```
Step 1: Customer adds items to cart                        [WebStorefront :5100]
  └── Product looked up in SQLite (storefront.db)
  └── Cart stored in IMemoryCache with session key

Step 2: Customer submits checkout                          [WebStorefront :5100]
  ├── GET /api/inventory/{productId} × N items             [→ InventoryApi :5102]
  │   └── Each item's QuantityAvailable checked
  └── POST /api/orders                                     [→ OrderApi :5101]
      ├── Order + OrderItems persisted in orders.db
      ├── Status set to PaymentValidated
      ├── traceparent/tracestate extracted from HTTP request
      ├── QueueMessage<OrderEvent> created with trace context
      └── JSON file written to order-events/pending/

Step 3: OrderProcessor polls queue (every 3 seconds)       [OrderProcessor - Windows Service]
  ├── File.Move() from pending/ to processing/ (atomic)
  ├── PUT /api/orders/{id}/status → Processing             [→ OrderApi :5101]
  ├── GET /api/orders/by-number/{num}                      [→ OrderApi :5101]
  ├── POST /api/inventory/reserve                          [→ InventoryApi :5102]
  │   └── Transactional: BeginTransaction → validate → reserve → Commit
  ├── PUT /api/orders/{id}/status → InventoryReserved      [→ OrderApi :5101]
  ├── PUT /api/orders/{id}/status → Fulfilled              [→ OrderApi :5101]
  ├── QueueMessage<FulfillmentEvent> written to fulfillment-events/pending/
  └── Original message moved to order-events/completed/

Step 4: NotificationWorker polls queue (every 5 seconds)   [NotificationWorker - Windows Service]
  ├── File.Move() from pending/ to processing/ (atomic)
  ├── Notification record created in notifications.db
  ├── Email simulated (500ms delay)
  ├── Notification status updated to Sent
  └── Message moved to fulfillment-events/completed/
```

**Per-order totals:**
- **Cross-service HTTP calls:** 7 (1 inventory check per item + 1 order creation + 1 order fetch + 1 inventory reservation + 3 status updates)
- **Asynchronous message queue hops:** 2 (order-events → fulfillment-events)
- **Database writes:** 3 databases (orders.db, inventory.db, notifications.db)
- **Processes involved:** 4 out of 5 (WebStorefront → OrderApi → OrderProcessor → InventoryApi + NotificationWorker)

---

## Frontend UI

The WebStorefront serves a single-page application from `wwwroot/index.html`. The frontend is built with vanilla HTML, CSS, and JavaScript — no build tools, no npm, no bundler.

**Pages:**
- **Shop** — Product grid with category labels, descriptions, SKUs, prices, quantity selectors, and add-to-cart buttons. Includes text search filtering.
- **Cart** — Cart item list with quantities, unit prices, line totals, remove buttons, and a checkout form (name, email, payment token). Shows order confirmation with order number after successful checkout.
- **Orders** — Lists all orders from the OrderApi (:5101) with order number, status badge (color-coded by state), customer info, line items, and total. Auto-refreshes to show status transitions.
- **Inventory** — Table view of all inventory from the InventoryApi (:5102) showing product name, SKU, on-hand, reserved, available (color-coded: green/yellow/red), and warehouse location.

The frontend calls the WebStorefront API (same origin) for products and cart operations, and calls the OrderApi (:5101) and InventoryApi (:5102) directly for the Orders and Inventory views (CORS enabled on both services).

---

## Windows Deployment

### Published Layout

```
C:\DemoApps\ecommerce\
├── services\
│   ├── WebStorefront\          ← Framework-dependent published app (win-x64)
│   │   ├── WebStorefront.exe
│   │   ├── WebStorefront.dll
│   │   ├── appsettings.json
│   │   ├── wwwroot\index.html
│   │   └── (EF Core, SQLite, shared library DLLs)
│   ├── OrderApi\
│   ├── InventoryApi\
│   ├── OrderProcessor\         ← Registered as Windows Service
│   └── NotificationWorker\     ← Registered as Windows Service
├── data\
│   ├── storefront.db
│   ├── orders.db
│   ├── inventory.db
│   └── notifications.db
├── queues\
│   ├── order-events\
│   │   ├── pending\
│   │   ├── processing\
│   │   ├── completed\
│   │   └── failed\
│   └── fulfillment-events\
│       ├── pending\
│       ├── processing\
│       ├── completed\
│       └── failed\
└── logs\
    ├── WebStorefront.log
    ├── WebStorefront-error.log
    ├── OrderApi.log
    ├── OrderApi-error.log
    ├── InventoryApi.log
    └── InventoryApi-error.log
```

### PowerShell Deployment Scripts

All scripts are in `scripts/` and require Administrator privileges (`#Requires -RunAsAdministrator`).

| Script | What It Does |
|--------|-------------|
| `setup.ps1` | Runs `dotnet publish -c Release -r win-x64 --self-contained false` for all 5 projects. Creates `services/`, `data/`, `queues/`, and `logs/` directory trees. |
| `install-services.ps1` | Registers `EcommerceOrderProcessor` and `EcommerceNotificationWorker` via `sc.exe create` with `start= delayed-auto`. Configures `sc.exe failure` for automatic restart. Sets service descriptions. Idempotent (removes existing service first). |
| `start-all.ps1` | Launches 3 web APIs via `Start-Process` with `-WindowStyle Hidden` and `-RedirectStandardOutput`/`-RedirectStandardError` to log files. Starts 2 Windows Services via `Start-Service`. Runs TCP health checks on ports 5100-5102. |
| `stop-all.ps1` | Stops web API processes by name via `Stop-Process`. Stops Windows Services via `Stop-Service`. |
| `uninstall-services.ps1` | Stops and deletes both Windows Services via `sc.exe delete`. Optionally removes data directories. |

### Windows Service Registration

```powershell
# How the services are registered (from install-services.ps1):
sc.exe create EcommerceOrderProcessor binPath= "C:\DemoApps\ecommerce\services\OrderProcessor\OrderProcessor.exe" start= delayed-auto
sc.exe failure EcommerceOrderProcessor reset= 86400 actions= restart/5000/restart/10000/restart/30000

sc.exe create EcommerceNotificationWorker binPath= "C:\DemoApps\ecommerce\services\NotificationWorker\NotificationWorker.exe" start= delayed-auto
sc.exe failure EcommerceNotificationWorker reset= 86400 actions= restart/5000/restart/10000/restart/30000
```

---

## API Reference

### WebStorefront (:5100)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/products` | List all 10 products |
| `GET` | `/api/products/{id}` | Get product by ID |
| `GET` | `/api/products/search?q={term}` | Search products by name or description |
| `GET` | `/api/cart/{sessionId}` | Get shopping cart for session |
| `POST` | `/api/cart/{sessionId}/items` | Add item to cart (`{ "productId": int, "quantity": int }`) |
| `DELETE` | `/api/cart/{sessionId}/items/{productId}` | Remove item from cart |
| `POST` | `/api/cart/{sessionId}/checkout` | Checkout cart (`{ "sessionId", "customerEmail", "customerName", "paymentToken" }`) |

### OrderApi (:5101)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/orders` | Create order and publish OrderEvent |
| `GET` | `/api/orders` | List all orders (with items) |
| `GET` | `/api/orders/{id}` | Get order by ID |
| `GET` | `/api/orders/by-number/{orderNumber}` | Get order by order number |
| `GET` | `/api/orders/pending` | List orders with Pending status |
| `PUT` | `/api/orders/{id}/status` | Update order status (`{ "status": "Processing" }`) |

### InventoryApi (:5102)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/inventory` | List all inventory items |
| `GET` | `/api/inventory/{productId}` | Get inventory for a product |
| `GET` | `/api/inventory/check?productId={id}&quantity={qty}` | Check stock availability |
| `POST` | `/api/inventory/reserve` | Reserve inventory (transactional) |
| `POST` | `/api/inventory/release` | Release reserved inventory |

---

## Running the Application

### Prerequisites

- **Windows Server 2019+** or Windows 10/11
- **.NET 8.0 SDK** (8.0.100 or later)
- **Administrator privileges** (for Windows Service registration)

### Quick Start (Development)

```powershell
# From the ecommerce-platform directory, run each in a separate terminal:
dotnet run --project src/WebStorefront
dotnet run --project src/OrderApi
dotnet run --project src/InventoryApi
dotnet run --project src/OrderProcessor
dotnet run --project src/NotificationWorker
```

### Production Deployment (Windows Server)

```powershell
# 1. Publish all services
.\scripts\setup.ps1

# 2. Register Windows Services
.\scripts\install-services.ps1

# 3. Start everything
.\scripts\start-all.ps1

# 4. Open browser
Start-Process http://localhost:5100
```

### Verify the Pipeline

```powershell
# Add item to cart
$body = '{"productId": 1, "quantity": 2}'
Invoke-RestMethod -Uri http://localhost:5100/api/cart/test-session/items -Method Post -Body $body -ContentType 'application/json'

# Checkout
$checkout = '{"sessionId": "test-session", "customerEmail": "user@example.com", "customerName": "Test User", "paymentToken": "tok_test_123"}'
Invoke-RestMethod -Uri http://localhost:5100/api/cart/test-session/checkout -Method Post -Body $checkout -ContentType 'application/json'

# Wait 15 seconds for async pipeline, then check results
Start-Sleep -Seconds 15

# Order should be Fulfilled
Invoke-RestMethod -Uri http://localhost:5101/api/orders -Method Get | Format-Table orderNumber, status, totalAmount

# Inventory should show increased QuantityReserved
Invoke-RestMethod -Uri http://localhost:5102/api/inventory/1 -Method Get

# Queue messages should be in completed/
Get-ChildItem C:\DemoApps\ecommerce\queues\order-events\completed
Get-ChildItem C:\DemoApps\ecommerce\queues\fulfillment-events\completed
```

### Teardown

```powershell
.\scripts\stop-all.ps1
.\scripts\uninstall-services.ps1
```
