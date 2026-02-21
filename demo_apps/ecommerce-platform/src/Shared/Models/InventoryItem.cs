namespace EcommercePlatform.Shared.Models;

public class InventoryItem
{
    public int Id { get; set; }
    public int ProductId { get; set; }
    public string Sku { get; set; } = string.Empty;
    public int QuantityOnHand { get; set; }
    public int QuantityReserved { get; set; }
    public int QuantityAvailable => QuantityOnHand - QuantityReserved;
    public string WarehouseLocation { get; set; } = string.Empty;
    public DateTime LastUpdated { get; set; } = DateTime.UtcNow;
}

public class ReservationRequest
{
    public string OrderNumber { get; set; } = string.Empty;
    public List<ReservationLine> Lines { get; set; } = new();
}

public class ReservationLine
{
    public int ProductId { get; set; }
    public string Sku { get; set; } = string.Empty;
    public int Quantity { get; set; }
}

public class ReservationResult
{
    public bool Success { get; set; }
    public string OrderNumber { get; set; } = string.Empty;
    public string? FailureReason { get; set; }
    public List<ReservationConfirmation> Confirmations { get; set; } = new();
}

public class ReservationConfirmation
{
    public string Sku { get; set; } = string.Empty;
    public int QuantityReserved { get; set; }
    public string WarehouseLocation { get; set; } = string.Empty;
}
