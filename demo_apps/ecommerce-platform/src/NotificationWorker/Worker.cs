using EcommercePlatform.NotificationWorker.Data;
using EcommercePlatform.Shared.Messaging;
using EcommercePlatform.Shared.Models;
using Microsoft.EntityFrameworkCore;

namespace EcommercePlatform.NotificationWorker;

public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly FileMessageQueue _queue;
    private readonly IServiceScopeFactory _scopeFactory;
    private int _loopCount;

    public Worker(ILogger<Worker> logger, FileMessageQueue queue, IServiceScopeFactory scopeFactory)
    {
        _logger = logger;
        _queue = queue;
        _scopeFactory = scopeFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("NotificationWorker starting at: {Time}", DateTimeOffset.Now);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                await ProcessFulfillmentEventsAsync(stoppingToken);

                // Every 6th loop (~30 seconds), retry any pending notifications
                _loopCount++;
                if (_loopCount % 6 == 0)
                {
                    await RetryPendingNotificationsAsync(stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error in NotificationWorker main loop");
            }

            await Task.Delay(TimeSpan.FromSeconds(5), stoppingToken);
        }

        _logger.LogInformation("NotificationWorker stopping at: {Time}", DateTimeOffset.Now);
    }

    private async Task ProcessFulfillmentEventsAsync(CancellationToken stoppingToken)
    {
        var (filePath, message) = await _queue.DequeueAsync<FulfillmentEvent>();

        if (message?.Payload == null)
        {
            return;
        }

        var evt = message.Payload;
        _logger.LogInformation(
            "Received fulfillment event for order {OrderNumber} (Success: {Success})",
            evt.OrderNumber, evt.Success);

        try
        {
            using var scope = _scopeFactory.CreateScope();
            var db = scope.ServiceProvider.GetRequiredService<NotificationDbContext>();

            // Create notification based on fulfillment result
            var notification = new Notification
            {
                OrderNumber = evt.OrderNumber,
                CustomerEmail = evt.CustomerEmail,
                CustomerName = evt.CustomerName,
                CreatedAt = DateTime.UtcNow,
                Status = NotificationStatus.Pending
            };

            if (evt.Success)
            {
                notification.Type = NotificationType.OrderFulfilled;
                notification.Subject = $"Your order {evt.OrderNumber} has been fulfilled!";
                notification.Body = $"Dear {evt.CustomerName},\n\n" +
                    $"Great news! Your order {evt.OrderNumber} has been successfully fulfilled " +
                    $"and is being prepared for shipment.\n\n" +
                    $"You will receive a shipping confirmation once your package is on its way.\n\n" +
                    $"Thank you for shopping with us!";
            }
            else
            {
                notification.Type = NotificationType.OrderFailed;
                notification.Subject = $"Issue with your order {evt.OrderNumber}";
                notification.Body = $"Dear {evt.CustomerName},\n\n" +
                    $"We're sorry, but there was an issue processing your order {evt.OrderNumber}.\n\n" +
                    $"Reason: {evt.FailureReason ?? "Unknown error"}\n\n" +
                    $"Our team has been notified and will look into this. " +
                    $"If you have any questions, please contact our support team.\n\n" +
                    $"We apologize for the inconvenience.";
            }

            // Save notification to database
            db.Notifications.Add(notification);
            await db.SaveChangesAsync(stoppingToken);
            _logger.LogInformation(
                "Created notification {NotificationId} for order {OrderNumber} (Type: {Type})",
                notification.Id, notification.OrderNumber, notification.Type);

            // Simulate sending email
            await SendEmailAsync(notification, stoppingToken);

            // Update notification status to Sent
            notification.Status = NotificationStatus.Sent;
            notification.SentAt = DateTime.UtcNow;
            await db.SaveChangesAsync(stoppingToken);
            _logger.LogInformation(
                "Notification {NotificationId} marked as Sent for order {OrderNumber}",
                notification.Id, notification.OrderNumber);

            // Mark queue message as complete
            _queue.Complete(filePath);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex,
                "Failed to process fulfillment event for order {OrderNumber}",
                evt.OrderNumber);
            _queue.Fail(filePath);
        }
    }

    private async Task SendEmailAsync(Notification notification, CancellationToken stoppingToken)
    {
        _logger.LogInformation(
            "Sending email to {CustomerEmail}: {Subject}",
            notification.CustomerEmail, notification.Subject);

        // Simulate email sending delay
        await Task.Delay(500, stoppingToken);

        _logger.LogInformation(
            "Email sent successfully to {CustomerEmail} for order {OrderNumber}",
            notification.CustomerEmail, notification.OrderNumber);
    }

    private async Task RetryPendingNotificationsAsync(CancellationToken stoppingToken)
    {
        using var scope = _scopeFactory.CreateScope();
        var db = scope.ServiceProvider.GetRequiredService<NotificationDbContext>();

        var pendingNotifications = await db.Notifications
            .Where(n => n.Status == NotificationStatus.Pending)
            .OrderBy(n => n.CreatedAt)
            .Take(10)
            .ToListAsync(stoppingToken);

        if (pendingNotifications.Count == 0)
        {
            return;
        }

        _logger.LogInformation(
            "Retrying {Count} pending notification(s)", pendingNotifications.Count);

        foreach (var notification in pendingNotifications)
        {
            try
            {
                await SendEmailAsync(notification, stoppingToken);

                notification.Status = NotificationStatus.Sent;
                notification.SentAt = DateTime.UtcNow;

                _logger.LogInformation(
                    "Retry successful for notification {NotificationId} (order {OrderNumber})",
                    notification.Id, notification.OrderNumber);
            }
            catch (Exception ex)
            {
                _logger.LogWarning(ex,
                    "Retry failed for notification {NotificationId} (order {OrderNumber})",
                    notification.Id, notification.OrderNumber);

                notification.Status = NotificationStatus.Failed;
            }
        }

        await db.SaveChangesAsync(stoppingToken);
    }
}
