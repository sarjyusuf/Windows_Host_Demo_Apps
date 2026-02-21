using System.Net.Http.Json;
using System.Text.Json;
using System.Text.Json.Serialization;
using Microsoft.Extensions.DependencyInjection;
using EcommercePlatform.Shared.Messaging;
using EcommercePlatform.Shared.Models;

namespace EcommercePlatform.OrderProcessor;

public class Worker : BackgroundService
{
    private readonly ILogger<Worker> _logger;
    private readonly FileMessageQueue _orderEventsQueue;
    private readonly FileMessageQueue _fulfillmentEventsQueue;
    private readonly IHttpClientFactory _httpClientFactory;

    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true,
        Converters = { new JsonStringEnumConverter() }
    };

    public Worker(
        ILogger<Worker> logger,
        [FromKeyedServices("order-events")] FileMessageQueue orderEventsQueue,
        [FromKeyedServices("fulfillment-events")] FileMessageQueue fulfillmentEventsQueue,
        IHttpClientFactory httpClientFactory)
    {
        _logger = logger;
        _orderEventsQueue = orderEventsQueue;
        _fulfillmentEventsQueue = fulfillmentEventsQueue;
        _httpClientFactory = httpClientFactory;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        _logger.LogInformation("OrderProcessor Worker started at {Time}", DateTimeOffset.Now);

        while (!stoppingToken.IsCancellationRequested)
        {
            try
            {
                var (filePath, message) = await _orderEventsQueue.DequeueAsync<OrderEvent>();

                if (message?.Payload != null)
                {
                    await ProcessOrderEventAsync(filePath, message, stoppingToken);
                }
            }
            catch (Exception ex)
            {
                _logger.LogError(ex, "Error in OrderProcessor polling loop");
            }

            await Task.Delay(TimeSpan.FromSeconds(3), stoppingToken);
        }

        _logger.LogInformation("OrderProcessor Worker stopping at {Time}", DateTimeOffset.Now);
    }

    private async Task ProcessOrderEventAsync(
        string filePath,
        QueueMessage<OrderEvent> message,
        CancellationToken stoppingToken)
    {
        var orderEvent = message.Payload!;
        _logger.LogInformation(
            "Received order event: OrderNumber={OrderNumber}, EventType={EventType}, OrderId={OrderId}",
            orderEvent.OrderNumber, orderEvent.EventType, orderEvent.OrderId);

        try
        {
            // Step 1: Update order status to Processing
            await UpdateOrderStatusAsync(orderEvent.OrderId, OrderStatus.Processing, message, stoppingToken);
            _logger.LogInformation("Order {OrderNumber} status set to Processing", orderEvent.OrderNumber);

            // Step 2: Fetch full order details to build reservation request
            var order = await GetOrderByNumberAsync(orderEvent.OrderNumber, message, stoppingToken);
            if (order == null)
            {
                _logger.LogError("Could not retrieve order {OrderNumber} from OrderApi", orderEvent.OrderNumber);
                await FailOrderAsync(orderEvent, message, "Order not found in OrderApi", filePath, stoppingToken);
                return;
            }

            // Step 3: Build reservation request from order items
            var reservationRequest = new ReservationRequest
            {
                OrderNumber = order.OrderNumber,
                Lines = order.Items.Select(item => new ReservationLine
                {
                    ProductId = item.ProductId,
                    Sku = item.Sku,
                    Quantity = item.Quantity
                }).ToList()
            };

            _logger.LogInformation(
                "Reserving inventory for order {OrderNumber} with {LineCount} line(s)",
                order.OrderNumber, reservationRequest.Lines.Count);

            // Step 4: Call InventoryApi to reserve inventory
            var reservationResult = await ReserveInventoryAsync(reservationRequest, message, stoppingToken);

            if (reservationResult != null && reservationResult.Success)
            {
                // Step 5a: Reservation succeeded
                _logger.LogInformation("Inventory reserved successfully for order {OrderNumber}", orderEvent.OrderNumber);

                await UpdateOrderStatusAsync(orderEvent.OrderId, OrderStatus.InventoryReserved, message, stoppingToken);
                _logger.LogInformation("Order {OrderNumber} status set to InventoryReserved", orderEvent.OrderNumber);

                await UpdateOrderStatusAsync(orderEvent.OrderId, OrderStatus.Fulfilled, message, stoppingToken);
                _logger.LogInformation("Order {OrderNumber} status set to Fulfilled", orderEvent.OrderNumber);

                // Publish fulfillment success event
                var fulfillmentEvent = new FulfillmentEvent
                {
                    OrderNumber = orderEvent.OrderNumber,
                    OrderId = orderEvent.OrderId,
                    CustomerEmail = orderEvent.CustomerEmail,
                    CustomerName = orderEvent.CustomerName,
                    Success = true,
                    Timestamp = DateTime.UtcNow
                };

                var fulfillmentMessage = new QueueMessage<FulfillmentEvent>
                {
                    Payload = fulfillmentEvent,
                    TraceParent = message.TraceParent,
                    TraceState = message.TraceState
                };

                await _fulfillmentEventsQueue.EnqueueAsync(fulfillmentMessage);
                _logger.LogInformation("Published fulfillment success event for order {OrderNumber}", orderEvent.OrderNumber);

                _orderEventsQueue.Complete(filePath);
                _logger.LogInformation("Order {OrderNumber} processing completed successfully", orderEvent.OrderNumber);
            }
            else
            {
                // Step 5b: Reservation failed
                var reason = reservationResult?.FailureReason ?? "Inventory reservation failed";
                _logger.LogWarning(
                    "Inventory reservation failed for order {OrderNumber}: {Reason}",
                    orderEvent.OrderNumber, reason);

                await FailOrderAsync(orderEvent, message, reason, filePath, stoppingToken);
            }
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Error processing order {OrderNumber}", orderEvent.OrderNumber);
            await FailOrderAsync(orderEvent, message, ex.Message, filePath, stoppingToken);
        }
    }

    private async Task FailOrderAsync(
        OrderEvent orderEvent,
        QueueMessage<OrderEvent> message,
        string reason,
        string filePath,
        CancellationToken stoppingToken)
    {
        try
        {
            await UpdateOrderStatusAsync(orderEvent.OrderId, OrderStatus.Failed, message, stoppingToken);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to update order {OrderNumber} status to Failed", orderEvent.OrderNumber);
        }

        var fulfillmentEvent = new FulfillmentEvent
        {
            OrderNumber = orderEvent.OrderNumber,
            OrderId = orderEvent.OrderId,
            CustomerEmail = orderEvent.CustomerEmail,
            CustomerName = orderEvent.CustomerName,
            Success = false,
            FailureReason = reason,
            Timestamp = DateTime.UtcNow
        };

        var fulfillmentMessage = new QueueMessage<FulfillmentEvent>
        {
            Payload = fulfillmentEvent,
            TraceParent = message.TraceParent,
            TraceState = message.TraceState
        };

        await _fulfillmentEventsQueue.EnqueueAsync(fulfillmentMessage);
        _logger.LogInformation("Published fulfillment failure event for order {OrderNumber}", orderEvent.OrderNumber);

        _orderEventsQueue.Fail(filePath);
    }

    private async Task UpdateOrderStatusAsync(
        int orderId,
        OrderStatus status,
        QueueMessage<OrderEvent> message,
        CancellationToken stoppingToken)
    {
        var client = _httpClientFactory.CreateClient("OrderApi");
        var request = new HttpRequestMessage(HttpMethod.Put, $"/api/orders/{orderId}/status")
        {
            Content = JsonContent.Create(new { status = status.ToString() }, options: JsonOptions)
        };

        AddTraceContext(request, message);

        var response = await client.SendAsync(request, stoppingToken);
        response.EnsureSuccessStatusCode();
    }

    private async Task<Order?> GetOrderByNumberAsync(
        string orderNumber,
        QueueMessage<OrderEvent> message,
        CancellationToken stoppingToken)
    {
        var client = _httpClientFactory.CreateClient("OrderApi");
        var request = new HttpRequestMessage(HttpMethod.Get, $"/api/orders/by-number/{orderNumber}");

        AddTraceContext(request, message);

        var response = await client.SendAsync(request, stoppingToken);
        response.EnsureSuccessStatusCode();

        return await response.Content.ReadFromJsonAsync<Order>(JsonOptions, stoppingToken);
    }

    private async Task<ReservationResult?> ReserveInventoryAsync(
        ReservationRequest reservationRequest,
        QueueMessage<OrderEvent> message,
        CancellationToken stoppingToken)
    {
        var client = _httpClientFactory.CreateClient("InventoryApi");
        var request = new HttpRequestMessage(HttpMethod.Post, "/api/inventory/reserve")
        {
            Content = JsonContent.Create(reservationRequest, options: JsonOptions)
        };

        AddTraceContext(request, message);

        var response = await client.SendAsync(request, stoppingToken);
        response.EnsureSuccessStatusCode();

        return await response.Content.ReadFromJsonAsync<ReservationResult>(JsonOptions, stoppingToken);
    }

    private static void AddTraceContext(HttpRequestMessage request, QueueMessage<OrderEvent> message)
    {
        if (!string.IsNullOrEmpty(message.TraceParent))
        {
            request.Headers.TryAddWithoutValidation("traceparent", message.TraceParent);
        }

        if (!string.IsNullOrEmpty(message.TraceState))
        {
            request.Headers.TryAddWithoutValidation("tracestate", message.TraceState);
        }
    }
}
