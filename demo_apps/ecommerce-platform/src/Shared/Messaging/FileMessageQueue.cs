using System.Text.Json;
using Microsoft.Extensions.Logging;

namespace EcommercePlatform.Shared.Messaging;

public class FileMessageQueue
{
    private readonly string _queueDirectory;
    private readonly string _processingDirectory;
    private readonly string _completedDirectory;
    private readonly string _failedDirectory;
    private readonly ILogger _logger;
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase
    };

    public FileMessageQueue(string baseDirectory, string queueName, ILogger logger)
    {
        _logger = logger;
        _queueDirectory = Path.Combine(baseDirectory, queueName, "pending");
        _processingDirectory = Path.Combine(baseDirectory, queueName, "processing");
        _completedDirectory = Path.Combine(baseDirectory, queueName, "completed");
        _failedDirectory = Path.Combine(baseDirectory, queueName, "failed");

        Directory.CreateDirectory(_queueDirectory);
        Directory.CreateDirectory(_processingDirectory);
        Directory.CreateDirectory(_completedDirectory);
        Directory.CreateDirectory(_failedDirectory);
    }

    public async Task EnqueueAsync<T>(QueueMessage<T> message)
    {
        var fileName = $"{message.EnqueuedAt:yyyyMMddHHmmssfff}_{message.MessageId}.json";
        var filePath = Path.Combine(_queueDirectory, fileName);
        var json = JsonSerializer.Serialize(message, JsonOptions);
        await File.WriteAllTextAsync(filePath, json);
        _logger.LogInformation("Enqueued message {MessageId} of type {MessageType} to {FilePath}",
            message.MessageId, message.MessageType, filePath);
    }

    public async Task<(string FilePath, QueueMessage<T>? Message)> DequeueAsync<T>()
    {
        var files = Directory.GetFiles(_queueDirectory, "*.json")
            .OrderBy(f => f)
            .ToList();

        foreach (var file in files)
        {
            var fileName = Path.GetFileName(file);
            var processingPath = Path.Combine(_processingDirectory, fileName);

            try
            {
                File.Move(file, processingPath);
                var json = await File.ReadAllTextAsync(processingPath);
                var message = JsonSerializer.Deserialize<QueueMessage<T>>(json, JsonOptions);
                _logger.LogInformation("Dequeued message {FileName} for processing", fileName);
                return (processingPath, message);
            }
            catch (IOException)
            {
                // File already taken by another consumer
                continue;
            }
        }

        return (string.Empty, null);
    }

    public void Complete(string processingFilePath)
    {
        if (string.IsNullOrEmpty(processingFilePath)) return;
        var fileName = Path.GetFileName(processingFilePath);
        var completedPath = Path.Combine(_completedDirectory, fileName);
        try
        {
            File.Move(processingFilePath, completedPath);
            _logger.LogInformation("Completed message {FileName}", fileName);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to move completed message {FileName}", fileName);
        }
    }

    public void Fail(string processingFilePath)
    {
        if (string.IsNullOrEmpty(processingFilePath)) return;
        var fileName = Path.GetFileName(processingFilePath);
        var failedPath = Path.Combine(_failedDirectory, fileName);
        try
        {
            File.Move(processingFilePath, failedPath);
            _logger.LogWarning("Failed message {FileName}", fileName);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Failed to move failed message {FileName}", fileName);
        }
    }

    public int GetPendingCount()
    {
        return Directory.Exists(_queueDirectory)
            ? Directory.GetFiles(_queueDirectory, "*.json").Length
            : 0;
    }
}
