using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using EcommercePlatform.OrderApi.Data;
using EcommercePlatform.Shared.Models;
using EcommercePlatform.Shared.Messaging;

namespace EcommercePlatform.OrderApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class OrdersController : ControllerBase
{
    private readonly OrderDbContext _db;
    private readonly FileMessageQueue _queue;
    private readonly ILogger<OrdersController> _logger;

    public OrdersController(OrderDbContext db, FileMessageQueue queue, ILogger<OrdersController> logger)
    {
        _db = db;
        _queue = queue;
        _logger = logger;
    }

    /// <summary>
    /// Create a new order. Saves to SQLite and publishes an OrderCreated event to the file queue.
    /// </summary>
    [HttpPost]
    public async Task<IActionResult> CreateOrder([FromBody] CreateOrderRequest request)
    {
        _logger.LogInformation("Creating order for {CustomerEmail}", request.CustomerEmail);

        var order = new Order
        {
            OrderNumber = $"ORD-{DateTimeOffset.UtcNow.ToUnixTimeMilliseconds()}",
            CustomerEmail = request.CustomerEmail,
            CustomerName = request.CustomerName,
            Status = OrderStatus.Pending,
            CreatedAt = DateTime.UtcNow,
            Items = request.Items.Select(i => new OrderItem
            {
                ProductId = i.ProductId,
                ProductName = i.ProductName,
                Sku = i.Sku,
                Quantity = i.Quantity,
                UnitPrice = i.UnitPrice
            }).ToList()
        };

        order.TotalAmount = order.Items.Sum(i => i.Quantity * i.UnitPrice);

        _db.Orders.Add(order);
        await _db.SaveChangesAsync();

        _logger.LogInformation("Order {OrderNumber} created with ID {OrderId}, total {TotalAmount}",
            order.OrderNumber, order.Id, order.TotalAmount);

        // Publish OrderCreated event to the file-based queue
        var orderEvent = new OrderEvent
        {
            OrderNumber = order.OrderNumber,
            EventType = "OrderCreated",
            OrderId = order.Id,
            CustomerEmail = order.CustomerEmail,
            CustomerName = order.CustomerName,
            TotalAmount = order.TotalAmount,
            Timestamp = DateTime.UtcNow
        };

        var message = new QueueMessage<OrderEvent>
        {
            Payload = orderEvent
        };

        // Forward distributed trace context from the incoming HTTP request
        if (Request.Headers.TryGetValue("traceparent", out var traceparent))
        {
            message.TraceParent = traceparent.ToString();
        }
        if (Request.Headers.TryGetValue("tracestate", out var tracestate))
        {
            message.TraceState = tracestate.ToString();
        }

        await _queue.EnqueueAsync(message);

        _logger.LogInformation("OrderCreated event published for {OrderNumber}", order.OrderNumber);

        return CreatedAtAction(nameof(GetOrder), new { id = order.Id }, order);
    }

    /// <summary>
    /// List all orders.
    /// </summary>
    [HttpGet]
    public async Task<IActionResult> GetOrders()
    {
        var orders = await _db.Orders
            .Include(o => o.Items)
            .OrderByDescending(o => o.CreatedAt)
            .ToListAsync();

        return Ok(orders);
    }

    /// <summary>
    /// Get a single order by ID.
    /// </summary>
    [HttpGet("{id:int}")]
    public async Task<IActionResult> GetOrder(int id)
    {
        var order = await _db.Orders
            .Include(o => o.Items)
            .FirstOrDefaultAsync(o => o.Id == id);

        if (order == null)
            return NotFound(new { message = $"Order with ID {id} not found" });

        return Ok(order);
    }

    /// <summary>
    /// Get a single order by order number.
    /// </summary>
    [HttpGet("by-number/{orderNumber}")]
    public async Task<IActionResult> GetOrderByNumber(string orderNumber)
    {
        var order = await _db.Orders
            .Include(o => o.Items)
            .FirstOrDefaultAsync(o => o.OrderNumber == orderNumber);

        if (order == null)
            return NotFound(new { message = $"Order '{orderNumber}' not found" });

        return Ok(order);
    }

    /// <summary>
    /// Update the status of an order (used by OrderProcessor and other downstream services).
    /// </summary>
    [HttpPut("{id:int}/status")]
    public async Task<IActionResult> UpdateOrderStatus(int id, [FromBody] UpdateStatusRequest request)
    {
        var order = await _db.Orders.FindAsync(id);
        if (order == null)
            return NotFound(new { message = $"Order with ID {id} not found" });

        var previousStatus = order.Status;
        order.Status = request.Status;

        // Set timestamp fields based on the new status
        if (request.Status == OrderStatus.Processing && order.ProcessedAt == null)
        {
            order.ProcessedAt = DateTime.UtcNow;
        }
        else if (request.Status == OrderStatus.Fulfilled && order.FulfilledAt == null)
        {
            order.FulfilledAt = DateTime.UtcNow;
        }

        await _db.SaveChangesAsync();

        _logger.LogInformation("Order {OrderId} status changed from {PreviousStatus} to {NewStatus}",
            id, previousStatus, request.Status);

        return Ok(order);
    }

    /// <summary>
    /// Get all orders with Pending status.
    /// </summary>
    [HttpGet("pending")]
    public async Task<IActionResult> GetPendingOrders()
    {
        var orders = await _db.Orders
            .Include(o => o.Items)
            .Where(o => o.Status == OrderStatus.Pending)
            .OrderBy(o => o.CreatedAt)
            .ToListAsync();

        return Ok(orders);
    }
}

/// <summary>
/// Request body for creating a new order.
/// </summary>
public class CreateOrderRequest
{
    public string CustomerEmail { get; set; } = string.Empty;
    public string CustomerName { get; set; } = string.Empty;
    public List<CreateOrderItemRequest> Items { get; set; } = new();
}

public class CreateOrderItemRequest
{
    public int ProductId { get; set; }
    public string ProductName { get; set; } = string.Empty;
    public string Sku { get; set; } = string.Empty;
    public int Quantity { get; set; }
    public decimal UnitPrice { get; set; }
}

/// <summary>
/// Request body for updating order status.
/// </summary>
public class UpdateStatusRequest
{
    public OrderStatus Status { get; set; }
}
