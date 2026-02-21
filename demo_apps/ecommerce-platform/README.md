# E-Commerce Microservices Platform

A distributed .NET 8 microservices application simulating an online store with an order processing pipeline. Designed to validate Datadog Single Step Instrumentation (SSI) on Windows hosts by exercising multiple independent .NET processes, cross-service HTTP trace propagation, asynchronous messaging, database operations, and Windows Service lifecycle management.

---

## Why This Application Is Complex

This is not a monolithic web app or a simple API. It is a **5-process distributed system** where each service runs as an independent .NET process with its own database, its own lifecycle, and its own communication patterns. The complexity is deliberate — it mirrors real-world production architectures that APM tools must instrument correctly:

1. **5 independent OS processes** — 3 ASP.NET Core Kestrel web APIs + 2 Worker Services running as Windows Services. Each process has its own PID, memory space, and runtime. An APM tool must discover and instrument all 5 independently.

2. **Two distinct process hosting models** — The web APIs are self-hosted Kestrel processes launched as background processes. The workers are registered with the Windows Service Control Manager (SCM) via `sc.exe` and managed through `UseWindowsService()`. SSI must handle both hosting models.

3. **Asynchronous message-based pipeline** — Orders don't complete synchronously. The WebStorefront creates an order via the OrderApi, which publishes an `OrderEvent` to a file-based message queue. The OrderProcessor (a separate Windows Service) polls the queue, processes the order, reserves inventory, and publishes a `FulfillmentEvent` to a second queue. The NotificationWorker (another Windows Service) picks up fulfillment events and sends notifications. A single checkout request triggers a chain across 4 services with 2 async messaging boundaries.

4. **Cross-service distributed tracing** — Every HTTP call and every queue message carries W3C Trace Context headers (`traceparent`, `tracestate`). The trace context originates at the WebStorefront, propagates to the OrderApi, gets embedded in the queue message payload, is extracted by the OrderProcessor, forwarded to the InventoryApi, and carried into the fulfillment queue for the NotificationWorker. A complete trace spans all 5 services across both synchronous HTTP and asynchronous file-based messaging.

5. **Multiple databases** — 3 separate SQLite databases (storefront products, orders, inventory) + 1 notifications database. Each service owns its data store. There is no shared database.

6. **Transactional inventory reservation** — The InventoryApi performs atomic inventory reservation within an EF Core transaction, checking availability and decrementing stock in a single database operation. Concurrent reservation requests are serialized.

7. **Failure handling and retry logic** — The OrderProcessor moves failed messages to a `failed/` directory and publishes failure events. The NotificationWorker retries unsent notifications every ~30 seconds. The pipeline handles partial failures gracefully.

---

## Architecture Overview

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
   └──────────────────┘       │     │  completed/ | failed/            │
                              │     └──────────────┬──────────────────┘
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

## Services

### 1. WebStorefront (ASP.NET Core Kestrel — Port 5100)

The customer-facing API that serves as the entry point for the entire platform.

**Responsibilities:**
- Product catalog browsing (10 seeded products across electronics, clothing, home categories)
- Shopping cart management using in-process memory cache (`IMemoryCache`) with 4-hour sliding expiration
- Checkout orchestration: validates inventory availability via InventoryApi, then creates the order via OrderApi
- W3C Trace Context propagation on all outbound HTTP calls

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Get product by ID |
| GET | `/api/products/search?q=` | Search products by name/description |
| GET | `/api/cart/{sessionId}` | Get shopping cart |
| POST | `/api/cart/{sessionId}/items` | Add item to cart |
| DELETE | `/api/cart/{sessionId}/items/{productId}` | Remove item from cart |
| POST | `/api/cart/{sessionId}/checkout` | Checkout (inventory check + order creation) |

**Dependencies:**
- `Microsoft.EntityFrameworkCore.Sqlite` — Product catalog database
- `IMemoryCache` — Shopping cart session storage
- `IHttpClientFactory` — Named HTTP clients for `OrderApi` and `InventoryApi`

**Cross-service calls during checkout:**
1. `GET /api/inventory/{productId}` (to InventoryApi) — for each cart item
2. `POST /api/orders` (to OrderApi) — to create the order

---

### 2. OrderApi (ASP.NET Core Kestrel — Port 5101)

The order management service that persists orders and publishes events for asynchronous processing.

**Responsibilities:**
- Order CRUD operations
- Payment token validation (simulated)
- Order number generation (`ORD-{timestamp}`)
- Publishing `OrderEvent` messages to the `order-events` file-based queue with embedded trace context
- Order status lifecycle management (Pending → PaymentValidated → Processing → InventoryReserved → Fulfilled → Shipped)

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/orders` | Create order + publish OrderEvent |
| GET | `/api/orders` | List all orders |
| GET | `/api/orders/{id}` | Get order by ID |
| GET | `/api/orders/by-number/{orderNumber}` | Get order by order number |
| PUT | `/api/orders/{id}/status` | Update order status |

**Dependencies:**
- `Microsoft.EntityFrameworkCore.Sqlite` — Orders and OrderItems database
- `FileMessageQueue` — Publishes `OrderEvent` messages to `order-events/pending/` directory
- `JsonStringEnumConverter` — Serializes `OrderStatus` enum as strings in API responses

**Event Publishing:**
When an order is created, the OrderApi:
1. Persists the order with status `PaymentValidated`
2. Extracts `traceparent`/`tracestate` headers from the incoming HTTP request
3. Creates a `QueueMessage<OrderEvent>` with the trace context embedded
4. Writes the message as a JSON file to `order-events/pending/`

---

### 3. InventoryApi (ASP.NET Core Kestrel — Port 5102)

The inventory management service responsible for stock levels and transactional reservations.

**Responsibilities:**
- Stock level queries (10 seeded inventory items matching the product catalog)
- Transactional inventory reservation for orders (atomic decrement within an EF Core transaction)
- Inventory release for cancelled orders

**Endpoints:**
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/inventory` | List all inventory items |
| GET | `/api/inventory/{productId}` | Get inventory for a product |
| POST | `/api/inventory/reserve` | Reserve inventory for an order (transactional) |
| POST | `/api/inventory/release` | Release previously reserved inventory |

**Dependencies:**
- `Microsoft.EntityFrameworkCore.Sqlite` — Inventory database with seed data

**Reservation Logic:**
The `/reserve` endpoint accepts a `ReservationRequest` with multiple `ReservationLine` items. Within a single database transaction:
1. Loads all requested inventory items
2. Validates each line has sufficient `QuantityAvailable` (= `QuantityOnHand - QuantityReserved`)
3. Increments `QuantityReserved` for each line
4. Commits or rolls back atomically

---

### 4. OrderProcessor (Worker Service / Windows Service)

The background order processing engine that bridges the asynchronous messaging pipeline between order creation and fulfillment.

**Responsibilities:**
- Polls the `order-events` file-based queue every 3 seconds
- Processes each order through a multi-step pipeline:
  1. Updates order status to `Processing` (HTTP PUT to OrderApi)
  2. Fetches full order details (HTTP GET to OrderApi)
  3. Builds an inventory reservation request from order items
  4. Reserves inventory (HTTP POST to InventoryApi)
  5. On success: updates status to `InventoryReserved` → `Fulfilled`, publishes `FulfillmentEvent`
  6. On failure: updates status to `Failed`, publishes failure `FulfillmentEvent`
- Moves processed messages to `completed/` or `failed/` directories
- Propagates trace context from queue messages to all outbound HTTP calls

**Process Hosting:**
- Uses `Microsoft.Extensions.Hosting.WindowsServices` with `UseWindowsService()`
- Registered as Windows Service `EcommerceOrderProcessor` via `sc.exe create`
- Startup type: Automatic (Delayed Start)
- Recovery: automatic restart on failure (5s, 10s, 30s intervals)
- Runs as LocalSystem account

**Dependencies:**
- `Microsoft.Extensions.Hosting.WindowsServices` — Windows Service hosting
- `Microsoft.Extensions.Http` — `IHttpClientFactory` (not included by default in Worker SDK, unlike ASP.NET Core)
- Keyed DI Services (`AddKeyedSingleton`) — Two `FileMessageQueue` instances: `order-events` (read) and `fulfillment-events` (write)
- Named `HttpClient` instances — `OrderApi` (localhost:5101) and `InventoryApi` (localhost:5102)
- `JsonStringEnumConverter` — For deserializing `OrderStatus` enum strings from OrderApi responses

---

### 5. NotificationWorker (Worker Service / Windows Service)

The notification service that watches for fulfilled orders and sends simulated customer notifications.

**Responsibilities:**
- Polls the `fulfillment-events` file-based queue every 5 seconds
- Creates notification records in its own SQLite database
- Generates contextual email content based on fulfillment result (success or failure)
- Simulates email sending (500ms delay)
- Retries pending notifications every ~30 seconds (every 6th polling loop)
- Tracks notification lifecycle: Pending → Sent / Failed

**Process Hosting:**
- Uses `Microsoft.Extensions.Hosting.WindowsServices` with `UseWindowsService()`
- Registered as Windows Service `EcommerceNotificationWorker` via `sc.exe create`
- Same recovery configuration as OrderProcessor

**Dependencies:**
- `Microsoft.Extensions.Hosting.WindowsServices` — Windows Service hosting
- `Microsoft.EntityFrameworkCore.Sqlite` — Notifications database
- `FileMessageQueue` — Reads from `fulfillment-events/pending/`

---

## Shared Library

The `Shared` project (`EcommercePlatform.Shared`) provides common code used across all services:

### Models
- **`Product`** — Id, Name, Description, Category, Price, Sku
- **`Order`** / **`OrderItem`** — Full order model with 8-state `OrderStatus` enum
- **`InventoryItem`** — Stock tracking with computed `QuantityAvailable`
- **`ReservationRequest`** / **`ReservationResult`** — Inventory reservation DTOs
- **`CartItem`** / **`ShoppingCart`** / **`CheckoutRequest`** — Shopping cart models
- **`Notification`** — Notification with `NotificationType` and `NotificationStatus` enums

### Messaging
- **`QueueMessage<T>`** — Generic message envelope with `MessageId`, `MessageType`, `Payload`, `EnqueuedAt`, `TraceParent`, `TraceState`, and custom `Headers`
- **`OrderEvent`** — Event published when an order is created (OrderNumber, OrderId, CustomerEmail, CustomerName, TotalAmount)
- **`FulfillmentEvent`** — Event published when order processing completes (Success/Failure with reason)
- **`FileMessageQueue`** — File-system-based message queue implementation

### File-Based Message Queue

The `FileMessageQueue` is a custom asynchronous messaging system that uses the filesystem as a message broker, eliminating the need for RabbitMQ, Redis Streams, or any external message broker.

**Directory Structure per Queue:**
```
queues/
├── order-events/
│   ├── pending/        ← New messages written here
│   ├── processing/     ← Message moved here when dequeued
│   ├── completed/      ← Message moved here on success
│   └── failed/         ← Message moved here on failure
└── fulfillment-events/
    ├── pending/
    ├── processing/
    ├── completed/
    └── failed/
```

**Message Lifecycle:**
1. **Enqueue** — Serializes `QueueMessage<T>` to JSON, writes to `pending/` with timestamped filename (`yyyyMMddHHmmssfff_{messageId}.json`)
2. **Dequeue** — Scans `pending/` sorted by filename (FIFO), atomically moves to `processing/` via `File.Move()` (OS-level atomic operation prevents double-processing)
3. **Complete** — Moves from `processing/` to `completed/`
4. **Fail** — Moves from `processing/` to `failed/`

**Concurrency Safety:** Uses `File.Move()` which is an atomic filesystem operation. If two consumers try to dequeue the same message, only one `File.Move()` succeeds — the other throws `IOException` and retries with the next message.

### ServiceDefaults

The `ServiceDefaults` class logs Datadog SSI-relevant environment variables on startup for each service:
- `DD_SERVICE`, `DD_ENV`, `DD_VERSION`
- `DD_TRACE_ENABLED`, `DD_AGENT_HOST`
- `COR_PROFILER`, `CORECLR_PROFILER`
- Process ID and Process Path

This enables validation that SSI has injected the correct profiler environment variables into each process.

---

## Frameworks and Tools

| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| Runtime | .NET 8 | 8.0 (LTS) | Application runtime |
| Web Framework | ASP.NET Core Kestrel | 8.0 | Self-hosted HTTP servers (no IIS) |
| Worker Hosting | `Microsoft.Extensions.Hosting.WindowsServices` | 8.0.1 | Windows Service integration |
| ORM | Entity Framework Core | 8.0.0 | Database access and migrations |
| Database | SQLite (via `Microsoft.EntityFrameworkCore.Sqlite`) | 8.0.0 | Embedded relational database |
| Caching | `Microsoft.Extensions.Caching.Memory` | (built-in) | In-process shopping cart cache |
| HTTP Clients | `IHttpClientFactory` / `Microsoft.Extensions.Http` | 8.0.0 | Cross-service HTTP communication |
| DI Framework | `Microsoft.Extensions.DependencyInjection` | (built-in) | Keyed services, named clients |
| Serialization | `System.Text.Json` | (built-in) | JSON serialization with `JsonStringEnumConverter` |
| Messaging | Custom `FileMessageQueue` | N/A | Filesystem-based async messaging |
| Distributed Tracing | W3C Trace Context (`traceparent`/`tracestate`) | N/A | Cross-service trace propagation |
| Service Registration | `sc.exe` | (Windows built-in) | Windows Service lifecycle |
| Process Management | `Start-Process` / `sc.exe start` | (Windows built-in) | Service startup |

---

## Deployment Layout (Windows)

```
C:\DemoApps\ecommerce\
├── services\
│   ├── WebStorefront\          ← Published ASP.NET Core app
│   │   ├── WebStorefront.exe
│   │   ├── appsettings.json
│   │   └── (runtime DLLs)
│   ├── OrderApi\
│   │   ├── OrderApi.exe
│   │   └── ...
│   ├── InventoryApi\
│   │   ├── InventoryApi.exe
│   │   └── ...
│   ├── OrderProcessor\
│   │   ├── OrderProcessor.exe   ← Registered as Windows Service
│   │   └── ...
│   └── NotificationWorker\
│       ├── NotificationWorker.exe  ← Registered as Windows Service
│       └── ...
├── data\
│   ├── storefront.db            ← Product catalog (SQLite)
│   ├── orders.db                ← Orders + items (SQLite)
│   ├── inventory.db             ← Inventory levels (SQLite)
│   └── notifications.db         ← Notification records (SQLite)
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

---

## PowerShell Scripts

All scripts are in `scripts/` and require Administrator privileges.

| Script | Purpose |
|--------|---------|
| `setup.ps1` | `dotnet publish` all 5 projects to `C:\DemoApps\ecommerce\services\`, create data/queue/log directories |
| `install-services.ps1` | Register `EcommerceOrderProcessor` and `EcommerceNotificationWorker` as Windows Services via `sc.exe create` with delayed-auto start and automatic restart on failure |
| `start-all.ps1` | Start 3 web APIs as background processes with stdout/stderr log redirection, start 2 Windows Services, run health checks |
| `stop-all.ps1` | Stop all web API processes and Windows Services |
| `uninstall-services.ps1` | Stop and delete Windows Services, optionally clean up data directories |

---

## End-to-End Order Flow

A single checkout triggers the following sequence across all 5 services:

```
1. Customer → POST /api/cart/{session}/items          [WebStorefront]
   └── Product looked up in SQLite, added to MemoryCache cart

2. Customer → POST /api/cart/{session}/checkout        [WebStorefront]
   ├── GET /api/inventory/{productId}                  [→ InventoryApi]  (availability check)
   └── POST /api/orders                                [→ OrderApi]      (order creation)
       ├── Order persisted in orders.db with status PaymentValidated
       └── OrderEvent written to order-events/pending/ (with traceparent)

3. OrderProcessor polls order-events/pending/          [OrderProcessor - Windows Service]
   ├── Moves message to processing/
   ├── PUT /api/orders/{id}/status → Processing        [→ OrderApi]
   ├── GET /api/orders/by-number/{num}                 [→ OrderApi]      (fetch full order)
   ├── POST /api/inventory/reserve                     [→ InventoryApi]  (atomic reservation)
   ├── PUT /api/orders/{id}/status → InventoryReserved [→ OrderApi]
   ├── PUT /api/orders/{id}/status → Fulfilled         [→ OrderApi]
   ├── FulfillmentEvent written to fulfillment-events/pending/
   └── Message moved to completed/

4. NotificationWorker polls fulfillment-events/pending/  [NotificationWorker - Windows Service]
   ├── Moves message to processing/
   ├── Creates Notification record in notifications.db
   ├── Simulates email send (500ms)
   ├── Updates notification status to Sent
   └── Message moved to completed/
```

**Total cross-service HTTP calls per order:** 7 (1 inventory check + 1 order creation + 4 status updates + 1 inventory reservation)
**Total async messaging hops:** 2 (order-events queue + fulfillment-events queue)
**Total databases written:** 3 (orders.db, inventory.db, notifications.db)

---

## SSI Validation Scenarios

This application exercises the following Datadog SSI validation targets:

| Scenario | How It's Tested |
|----------|----------------|
| **Process discovery** | 5 independent .NET processes that SSI must discover |
| **Multiple process types** | 3 ASP.NET Core web hosts + 2 Worker Service hosts |
| **Windows Services** | OrderProcessor and NotificationWorker registered via SCM |
| **Background workers** | Both Windows Services use `BackgroundService` polling loops |
| **Async messaging boundary** | File-based queue with trace context in message payload |
| **Cross-service trace propagation** | `traceparent`/`tracestate` forwarded on HTTP + embedded in queue messages |
| **Database operations** | 4 SQLite databases with EF Core (queries, inserts, transactions) |
| **In-process caching** | `IMemoryCache` for shopping cart |
| **HTTP client calls** | `IHttpClientFactory` with named clients across services |
| **Enum serialization** | `JsonStringEnumConverter` for OrderStatus across service boundaries |
| **Service restart/recovery** | `sc.exe failure` configured with automatic restart |
| **Delayed auto-start** | Windows Services configured for delayed automatic startup |

---

## Running Locally (Development)

**Prerequisites:** .NET 8 SDK

```powershell
# From the ecommerce-platform directory

# Build all projects
dotnet build src/WebStorefront/WebStorefront.csproj
dotnet build src/OrderApi/OrderApi.csproj
dotnet build src/InventoryApi/InventoryApi.csproj
dotnet build src/OrderProcessor/OrderProcessor.csproj
dotnet build src/NotificationWorker/NotificationWorker.csproj

# Run each in a separate terminal
dotnet run --project src/WebStorefront
dotnet run --project src/OrderApi
dotnet run --project src/InventoryApi
dotnet run --project src/OrderProcessor
dotnet run --project src/NotificationWorker
```

**On a Windows Server (production-like):**

```powershell
# Publish, install services, and start everything
.\scripts\setup.ps1
.\scripts\install-services.ps1
.\scripts\start-all.ps1
```

---

## Testing the Pipeline

```powershell
# Add item to cart
$body = '{"productId": 1, "quantity": 2}'
Invoke-RestMethod -Uri http://localhost:5100/api/cart/test-session/items -Method Post -Body $body -ContentType 'application/json'

# Checkout
$checkout = '{"sessionId": "test-session", "customerEmail": "user@example.com", "customerName": "Test User", "paymentToken": "tok_test_123"}'
Invoke-RestMethod -Uri http://localhost:5100/api/cart/test-session/checkout -Method Post -Body $checkout -ContentType 'application/json'

# Wait ~15 seconds for async pipeline

# Check order status (should be Fulfilled)
Invoke-RestMethod -Uri http://localhost:5101/api/orders -Method Get

# Check inventory (QuantityReserved should have increased)
Invoke-RestMethod -Uri http://localhost:5102/api/inventory -Method Get

# Check queue status
Get-ChildItem C:\DemoApps\ecommerce\queues\order-events\completed
Get-ChildItem C:\DemoApps\ecommerce\queues\fulfillment-events\completed
```
