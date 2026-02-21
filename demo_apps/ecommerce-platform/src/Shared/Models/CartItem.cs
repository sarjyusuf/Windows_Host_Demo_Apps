namespace EcommercePlatform.Shared.Models;

public class CartItem
{
    public int ProductId { get; set; }
    public string ProductName { get; set; } = string.Empty;
    public string Sku { get; set; } = string.Empty;
    public decimal UnitPrice { get; set; }
    public int Quantity { get; set; }
}

public class ShoppingCart
{
    public string SessionId { get; set; } = string.Empty;
    public List<CartItem> Items { get; set; } = new();
    public decimal Total => Items.Sum(i => i.UnitPrice * i.Quantity);
    public DateTime LastUpdated { get; set; } = DateTime.UtcNow;
}

public class CheckoutRequest
{
    public string SessionId { get; set; } = string.Empty;
    public string CustomerEmail { get; set; } = string.Empty;
    public string CustomerName { get; set; } = string.Empty;
    public string PaymentToken { get; set; } = string.Empty;
}
