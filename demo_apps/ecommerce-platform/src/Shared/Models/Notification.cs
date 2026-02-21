namespace EcommercePlatform.Shared.Models;

public class Notification
{
    public int Id { get; set; }
    public string OrderNumber { get; set; } = string.Empty;
    public string CustomerEmail { get; set; } = string.Empty;
    public string CustomerName { get; set; } = string.Empty;
    public NotificationType Type { get; set; }
    public string Subject { get; set; } = string.Empty;
    public string Body { get; set; } = string.Empty;
    public NotificationStatus Status { get; set; } = NotificationStatus.Pending;
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? SentAt { get; set; }
}

public enum NotificationType
{
    OrderConfirmation,
    OrderProcessing,
    OrderFulfilled,
    OrderShipped,
    OrderFailed
}

public enum NotificationStatus
{
    Pending,
    Sent,
    Failed
}
