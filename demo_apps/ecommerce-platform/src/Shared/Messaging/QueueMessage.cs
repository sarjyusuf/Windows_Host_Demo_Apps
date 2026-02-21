namespace EcommercePlatform.Shared.Messaging;

public class QueueMessage<T>
{
    public string MessageId { get; set; } = Guid.NewGuid().ToString("N");
    public string MessageType { get; set; } = typeof(T).Name;
    public T? Payload { get; set; }
    public DateTime EnqueuedAt { get; set; } = DateTime.UtcNow;
    public string? TraceParent { get; set; }
    public string? TraceState { get; set; }
    public Dictionary<string, string> Headers { get; set; } = new();
}

public class OrderEvent
{
    public string OrderNumber { get; set; } = string.Empty;
    public string EventType { get; set; } = string.Empty;
    public int OrderId { get; set; }
    public string CustomerEmail { get; set; } = string.Empty;
    public string CustomerName { get; set; } = string.Empty;
    public decimal TotalAmount { get; set; }
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
}

public class FulfillmentEvent
{
    public string OrderNumber { get; set; } = string.Empty;
    public int OrderId { get; set; }
    public string CustomerEmail { get; set; } = string.Empty;
    public string CustomerName { get; set; } = string.Empty;
    public bool Success { get; set; }
    public string? FailureReason { get; set; }
    public DateTime Timestamp { get; set; } = DateTime.UtcNow;
}
