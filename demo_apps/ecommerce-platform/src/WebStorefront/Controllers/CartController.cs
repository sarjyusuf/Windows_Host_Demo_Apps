using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Caching.Memory;
using EcommercePlatform.Shared.Models;
using EcommercePlatform.WebStorefront.Data;

namespace EcommercePlatform.WebStorefront.Controllers;

[ApiController]
[Route("api/cart")]
public class CartController : ControllerBase
{
    private readonly IMemoryCache _cache;
    private readonly StorefrontDbContext _db;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<CartController> _logger;
    private readonly IConfiguration _configuration;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true
    };

    private static readonly TimeSpan CartExpiration = TimeSpan.FromHours(4);

    public CartController(
        IMemoryCache cache,
        StorefrontDbContext db,
        IHttpClientFactory httpClientFactory,
        ILogger<CartController> logger,
        IConfiguration configuration)
    {
        _cache = cache;
        _db = db;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
        _configuration = configuration;
    }

    /// <summary>
    /// GET /api/cart/{sessionId} - Get the shopping cart for a session.
    /// </summary>
    [HttpGet("{sessionId}")]
    public ActionResult<ShoppingCart> GetCart(string sessionId)
    {
        _logger.LogInformation("Getting cart for session {SessionId}", sessionId);
        var cart = GetOrCreateCart(sessionId);
        return Ok(cart);
    }

    /// <summary>
    /// POST /api/cart/{sessionId}/items - Add an item to the cart.
    /// Expects JSON body: { "productId": int, "quantity": int }
    /// </summary>
    [HttpPost("{sessionId}/items")]
    public async Task<ActionResult<ShoppingCart>> AddItem(string sessionId, [FromBody] AddItemRequest request)
    {
        if (request.Quantity <= 0)
        {
            return BadRequest(new { message = "Quantity must be greater than zero." });
        }

        _logger.LogInformation("Adding product {ProductId} (qty: {Quantity}) to cart {SessionId}",
            request.ProductId, request.Quantity, sessionId);

        var product = await _db.Products.AsNoTracking().FirstOrDefaultAsync(p => p.Id == request.ProductId);
        if (product is null)
        {
            _logger.LogWarning("Product {ProductId} not found when adding to cart", request.ProductId);
            return NotFound(new { message = $"Product with ID {request.ProductId} not found." });
        }

        var cart = GetOrCreateCart(sessionId);

        var existingItem = cart.Items.FirstOrDefault(i => i.ProductId == request.ProductId);
        if (existingItem is not null)
        {
            existingItem.Quantity += request.Quantity;
            _logger.LogInformation("Updated quantity for product {ProductId} in cart {SessionId} to {Quantity}",
                request.ProductId, sessionId, existingItem.Quantity);
        }
        else
        {
            cart.Items.Add(new CartItem
            {
                ProductId = product.Id,
                ProductName = product.Name,
                Sku = product.Sku,
                UnitPrice = product.Price,
                Quantity = request.Quantity
            });
            _logger.LogInformation("Added new product {ProductId} to cart {SessionId}", request.ProductId, sessionId);
        }

        cart.LastUpdated = DateTime.UtcNow;
        SaveCart(cart);

        return Ok(cart);
    }

    /// <summary>
    /// DELETE /api/cart/{sessionId}/items/{productId} - Remove an item from the cart.
    /// </summary>
    [HttpDelete("{sessionId}/items/{productId:int}")]
    public ActionResult<ShoppingCart> RemoveItem(string sessionId, int productId)
    {
        _logger.LogInformation("Removing product {ProductId} from cart {SessionId}", productId, sessionId);
        var cart = GetOrCreateCart(sessionId);

        var item = cart.Items.FirstOrDefault(i => i.ProductId == productId);
        if (item is null)
        {
            return NotFound(new { message = $"Product {productId} not found in cart." });
        }

        cart.Items.Remove(item);
        cart.LastUpdated = DateTime.UtcNow;
        SaveCart(cart);

        _logger.LogInformation("Removed product {ProductId} from cart {SessionId}", productId, sessionId);
        return Ok(cart);
    }

    /// <summary>
    /// POST /api/cart/{sessionId}/checkout - Check inventory availability via InventoryApi,
    /// then create an order via OrderApi. Forwards distributed trace headers.
    /// </summary>
    [HttpPost("{sessionId}/checkout")]
    public async Task<ActionResult> Checkout(string sessionId, [FromBody] CheckoutRequest request)
    {
        _logger.LogInformation("Starting checkout for session {SessionId}", sessionId);

        var cart = GetOrCreateCart(sessionId);
        if (cart.Items.Count == 0)
        {
            return BadRequest(new { message = "Cart is empty. Add items before checking out." });
        }

        // --- Step 1: Check inventory availability via InventoryApi ---
        _logger.LogInformation("Checking inventory availability for {ItemCount} item(s)", cart.Items.Count);

        var inventoryClient = _httpClientFactory.CreateClient("InventoryApi");
        ForwardTraceHeaders(inventoryClient);

        var availabilityFailed = false;
        var unavailableItems = new List<string>();

        foreach (var item in cart.Items)
        {
            try
            {
                var response = await inventoryClient.GetAsync($"/api/inventory/{item.ProductId}");
                if (response.IsSuccessStatusCode)
                {
                    var json = await response.Content.ReadAsStringAsync();
                    var inventoryItem = JsonSerializer.Deserialize<InventoryItem>(json, JsonOptions);
                    if (inventoryItem is null || inventoryItem.QuantityAvailable < item.Quantity)
                    {
                        var available = inventoryItem?.QuantityAvailable ?? 0;
                        _logger.LogWarning(
                            "Insufficient inventory for product {ProductId} ({Sku}): requested {Requested}, available {Available}",
                            item.ProductId, item.Sku, item.Quantity, available);
                        unavailableItems.Add(
                            $"{item.ProductName} (requested: {item.Quantity}, available: {available})");
                        availabilityFailed = true;
                    }
                }
                else
                {
                    _logger.LogWarning("Inventory check failed for product {ProductId}: HTTP {StatusCode}",
                        item.ProductId, (int)response.StatusCode);
                    unavailableItems.Add($"{item.ProductName} (inventory check failed)");
                    availabilityFailed = true;
                }
            }
            catch (HttpRequestException ex)
            {
                _logger.LogError(ex, "Failed to reach InventoryApi for product {ProductId}", item.ProductId);
                unavailableItems.Add($"{item.ProductName} (inventory service unavailable)");
                availabilityFailed = true;
            }
        }

        if (availabilityFailed)
        {
            return Conflict(new
            {
                message = "Some items are not available in the requested quantity.",
                unavailableItems
            });
        }

        _logger.LogInformation("Inventory check passed for all items in cart {SessionId}", sessionId);

        // --- Step 2: Create order via OrderApi ---
        _logger.LogInformation("Creating order via OrderApi for session {SessionId}", sessionId);

        var orderClient = _httpClientFactory.CreateClient("OrderApi");
        ForwardTraceHeaders(orderClient);

        var createOrderPayload = new
        {
            customerEmail = request.CustomerEmail,
            customerName = request.CustomerName,
            paymentToken = request.PaymentToken,
            items = cart.Items.Select(ci => new
            {
                productId = ci.ProductId,
                productName = ci.ProductName,
                sku = ci.Sku,
                quantity = ci.Quantity,
                unitPrice = ci.UnitPrice
            }).ToList()
        };

        var orderJson = JsonSerializer.Serialize(createOrderPayload, JsonOptions);
        var orderContent = new StringContent(orderJson, Encoding.UTF8, "application/json");

        try
        {
            var orderResponse = await orderClient.PostAsync("/api/orders", orderContent);
            var orderResponseBody = await orderResponse.Content.ReadAsStringAsync();

            if (!orderResponse.IsSuccessStatusCode)
            {
                _logger.LogError("OrderApi returned {StatusCode}: {Body}",
                    (int)orderResponse.StatusCode, orderResponseBody);
                return StatusCode((int)orderResponse.StatusCode, new
                {
                    message = "Failed to create order.",
                    detail = orderResponseBody
                });
            }

            _logger.LogInformation("Order created successfully for session {SessionId}", sessionId);

            // Clear the cart after successful order creation
            _cache.Remove(CartCacheKey(sessionId));
            _logger.LogInformation("Cart cleared for session {SessionId} after successful checkout", sessionId);

            var order = JsonSerializer.Deserialize<JsonElement>(orderResponseBody);
            return Ok(new
            {
                message = "Checkout successful. Your order has been placed.",
                order
            });
        }
        catch (HttpRequestException ex)
        {
            _logger.LogError(ex, "Failed to reach OrderApi during checkout for session {SessionId}", sessionId);
            return StatusCode(503, new { message = "Order service is unavailable. Please try again later." });
        }
    }

    // ---- Private helpers ----

    private ShoppingCart GetOrCreateCart(string sessionId)
    {
        var key = CartCacheKey(sessionId);
        if (_cache.TryGetValue(key, out ShoppingCart? cart) && cart is not null)
        {
            return cart;
        }

        cart = new ShoppingCart { SessionId = sessionId };
        SaveCart(cart);
        return cart;
    }

    private void SaveCart(ShoppingCart cart)
    {
        var options = new MemoryCacheEntryOptions
        {
            SlidingExpiration = CartExpiration
        };
        _cache.Set(CartCacheKey(cart.SessionId), cart, options);
    }

    private static string CartCacheKey(string sessionId) => $"cart:{sessionId}";

    /// <summary>
    /// Forwards the W3C Trace Context headers (traceparent, tracestate) from the incoming
    /// request to the outgoing HttpClient so that distributed traces remain connected
    /// across service boundaries.
    /// </summary>
    private void ForwardTraceHeaders(HttpClient client)
    {
        if (Request.Headers.TryGetValue("traceparent", out var traceparent))
        {
            client.DefaultRequestHeaders.TryAddWithoutValidation("traceparent", traceparent.ToString());
        }

        if (Request.Headers.TryGetValue("tracestate", out var tracestate))
        {
            client.DefaultRequestHeaders.TryAddWithoutValidation("tracestate", tracestate.ToString());
        }
    }
}

/// <summary>
/// Request body for adding an item to the cart.
/// </summary>
public class AddItemRequest
{
    public int ProductId { get; set; }
    public int Quantity { get; set; } = 1;
}
