using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using EcommercePlatform.InventoryApi.Data;
using EcommercePlatform.Shared.Models;

namespace EcommercePlatform.InventoryApi.Controllers;

[ApiController]
[Route("api/[controller]")]
public class InventoryController : ControllerBase
{
    private readonly InventoryDbContext _db;
    private readonly ILogger<InventoryController> _logger;

    public InventoryController(InventoryDbContext db, ILogger<InventoryController> logger)
    {
        _db = db;
        _logger = logger;
    }

    /// <summary>
    /// GET /api/inventory - List all inventory items.
    /// </summary>
    [HttpGet]
    public async Task<ActionResult<List<InventoryItem>>> GetAll()
    {
        _logger.LogInformation("Fetching all inventory items");
        var items = await _db.InventoryItems.ToListAsync();
        return Ok(items);
    }

    /// <summary>
    /// GET /api/inventory/{productId} - Get inventory for a specific product.
    /// </summary>
    [HttpGet("{productId:int}")]
    public async Task<ActionResult<InventoryItem>> GetByProductId(int productId)
    {
        _logger.LogInformation("Fetching inventory for ProductId {ProductId}", productId);
        var item = await _db.InventoryItems.FirstOrDefaultAsync(i => i.ProductId == productId);

        if (item == null)
        {
            _logger.LogWarning("Inventory not found for ProductId {ProductId}", productId);
            return NotFound(new { Message = $"No inventory record found for product {productId}" });
        }

        return Ok(item);
    }

    /// <summary>
    /// GET /api/inventory/check?productId=X&amp;quantity=Y - Check stock availability.
    /// </summary>
    [HttpGet("check")]
    public async Task<ActionResult> CheckAvailability([FromQuery] int productId, [FromQuery] int quantity)
    {
        _logger.LogInformation("Checking availability for ProductId {ProductId}, Quantity {Quantity}",
            productId, quantity);

        var item = await _db.InventoryItems.FirstOrDefaultAsync(i => i.ProductId == productId);

        if (item == null)
        {
            return Ok(new
            {
                Available = false,
                QuantityAvailable = 0,
                Message = $"No inventory record found for product {productId}"
            });
        }

        var quantityAvailable = item.QuantityOnHand - item.QuantityReserved;
        return Ok(new
        {
            Available = quantityAvailable >= quantity,
            QuantityAvailable = quantityAvailable
        });
    }

    /// <summary>
    /// POST /api/inventory/reserve - Reserve inventory for an order.
    /// Uses a transaction; rolls back all changes if any line fails.
    /// </summary>
    [HttpPost("reserve")]
    public async Task<ActionResult<ReservationResult>> Reserve([FromBody] ReservationRequest request)
    {
        _logger.LogInformation("Processing reservation for Order {OrderNumber} with {LineCount} lines",
            request.OrderNumber, request.Lines.Count);

        using var transaction = await _db.Database.BeginTransactionAsync();

        try
        {
            var confirmations = new List<ReservationConfirmation>();

            foreach (var line in request.Lines)
            {
                var item = await _db.InventoryItems
                    .FirstOrDefaultAsync(i => i.ProductId == line.ProductId);

                if (item == null)
                {
                    await transaction.RollbackAsync();
                    _logger.LogWarning("Reservation failed for Order {OrderNumber}: " +
                        "Product {ProductId} not found in inventory",
                        request.OrderNumber, line.ProductId);

                    return Ok(new ReservationResult
                    {
                        Success = false,
                        OrderNumber = request.OrderNumber,
                        FailureReason = $"Product {line.ProductId} (SKU: {line.Sku}) not found in inventory"
                    });
                }

                var quantityAvailable = item.QuantityOnHand - item.QuantityReserved;
                if (quantityAvailable < line.Quantity)
                {
                    await transaction.RollbackAsync();
                    _logger.LogWarning("Reservation failed for Order {OrderNumber}: " +
                        "Insufficient stock for Product {ProductId}. " +
                        "Requested: {Requested}, Available: {Available}",
                        request.OrderNumber, line.ProductId, line.Quantity, quantityAvailable);

                    return Ok(new ReservationResult
                    {
                        Success = false,
                        OrderNumber = request.OrderNumber,
                        FailureReason = $"Insufficient stock for {line.Sku}. " +
                            $"Requested: {line.Quantity}, Available: {quantityAvailable}"
                    });
                }

                item.QuantityReserved += line.Quantity;
                item.LastUpdated = DateTime.UtcNow;

                confirmations.Add(new ReservationConfirmation
                {
                    Sku = item.Sku,
                    QuantityReserved = line.Quantity,
                    WarehouseLocation = item.WarehouseLocation
                });

                _logger.LogInformation("Reserved {Quantity} of {Sku} at {Location} for Order {OrderNumber}",
                    line.Quantity, item.Sku, item.WarehouseLocation, request.OrderNumber);
            }

            await _db.SaveChangesAsync();
            await transaction.CommitAsync();

            _logger.LogInformation("Reservation completed successfully for Order {OrderNumber}",
                request.OrderNumber);

            return Ok(new ReservationResult
            {
                Success = true,
                OrderNumber = request.OrderNumber,
                Confirmations = confirmations
            });
        }
        catch (Exception ex)
        {
            await transaction.RollbackAsync();
            _logger.LogError(ex, "Reservation failed for Order {OrderNumber} due to an unexpected error",
                request.OrderNumber);

            return StatusCode(500, new ReservationResult
            {
                Success = false,
                OrderNumber = request.OrderNumber,
                FailureReason = "An unexpected error occurred while processing the reservation"
            });
        }
    }

    /// <summary>
    /// POST /api/inventory/release - Release previously reserved inventory.
    /// </summary>
    [HttpPost("release")]
    public async Task<ActionResult<ReservationResult>> Release([FromBody] ReservationRequest request)
    {
        _logger.LogInformation("Processing release for Order {OrderNumber} with {LineCount} lines",
            request.OrderNumber, request.Lines.Count);

        using var transaction = await _db.Database.BeginTransactionAsync();

        try
        {
            var confirmations = new List<ReservationConfirmation>();

            foreach (var line in request.Lines)
            {
                var item = await _db.InventoryItems
                    .FirstOrDefaultAsync(i => i.ProductId == line.ProductId);

                if (item == null)
                {
                    _logger.LogWarning("Release: Product {ProductId} not found in inventory, skipping",
                        line.ProductId);
                    continue;
                }

                var quantityToRelease = Math.Min(line.Quantity, item.QuantityReserved);
                item.QuantityReserved -= quantityToRelease;
                item.LastUpdated = DateTime.UtcNow;

                confirmations.Add(new ReservationConfirmation
                {
                    Sku = item.Sku,
                    QuantityReserved = quantityToRelease,
                    WarehouseLocation = item.WarehouseLocation
                });

                _logger.LogInformation("Released {Quantity} of {Sku} at {Location} for Order {OrderNumber}",
                    quantityToRelease, item.Sku, item.WarehouseLocation, request.OrderNumber);
            }

            await _db.SaveChangesAsync();
            await transaction.CommitAsync();

            _logger.LogInformation("Release completed successfully for Order {OrderNumber}",
                request.OrderNumber);

            return Ok(new ReservationResult
            {
                Success = true,
                OrderNumber = request.OrderNumber,
                Confirmations = confirmations
            });
        }
        catch (Exception ex)
        {
            await transaction.RollbackAsync();
            _logger.LogError(ex, "Release failed for Order {OrderNumber} due to an unexpected error",
                request.OrderNumber);

            return StatusCode(500, new ReservationResult
            {
                Success = false,
                OrderNumber = request.OrderNumber,
                FailureReason = "An unexpected error occurred while processing the release"
            });
        }
    }
}
