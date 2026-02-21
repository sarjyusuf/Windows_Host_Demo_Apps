using EcommercePlatform.OrderProcessor;
using EcommercePlatform.Shared;
using EcommercePlatform.Shared.Messaging;

var host = Host.CreateDefaultBuilder(args)
    .UseWindowsService()
    .ConfigureServices((context, services) =>
    {
        var config = context.Configuration;
        var queueBasePath = config["QueueBasePath"] ?? @"C:\DemoApps\ecommerce\queues";
        var orderApiUrl = config["OrderApiUrl"] ?? "http://localhost:5101";
        var inventoryApiUrl = config["InventoryApiUrl"] ?? "http://localhost:5102";

        // Register FileMessageQueue for reading order-events
        services.AddKeyedSingleton("order-events", (sp, _) =>
        {
            var logger = sp.GetRequiredService<ILoggerFactory>().CreateLogger("FileMessageQueue.OrderEvents");
            return new FileMessageQueue(queueBasePath, "order-events", logger);
        });

        // Register FileMessageQueue for writing fulfillment-events
        services.AddKeyedSingleton("fulfillment-events", (sp, _) =>
        {
            var logger = sp.GetRequiredService<ILoggerFactory>().CreateLogger("FileMessageQueue.FulfillmentEvents");
            return new FileMessageQueue(queueBasePath, "fulfillment-events", logger);
        });

        // Register named HttpClients for OrderApi and InventoryApi
        services.AddHttpClient("OrderApi", client =>
        {
            client.BaseAddress = new Uri(orderApiUrl);
            client.Timeout = TimeSpan.FromSeconds(30);
        });

        services.AddHttpClient("InventoryApi", client =>
        {
            client.BaseAddress = new Uri(inventoryApiUrl);
            client.Timeout = TimeSpan.FromSeconds(30);
        });

        services.AddHostedService<Worker>();
    })
    .Build();

// Log Datadog SSI configuration on startup
var logger = host.Services.GetRequiredService<ILoggerFactory>().CreateLogger("OrderProcessor");
ServiceDefaults.LogDatadogConfig(logger, "OrderProcessor");

host.Run();
