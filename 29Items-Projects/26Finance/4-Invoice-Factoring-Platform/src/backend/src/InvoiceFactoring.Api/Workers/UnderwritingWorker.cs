using Azure.Messaging.ServiceBus;
using InvoiceFactoring.Application.Features.Underwriting.Commands.AssessInvoice;
using InvoiceFactoring.Application.Features.Underwriting.Contracts;
using InvoiceFactoring.Infrastructure.Messaging.ServiceBus;
using MediatR;
using Microsoft.Extensions.Options;

namespace InvoiceFactoring.Api.Workers;

/// <summary>
/// Consumes the underwriting queue and runs the scoring use case for each invoice off the
/// request thread (ARCHITECTURE §2.3). Messages are completed on success and abandoned on
/// failure so Service Bus retries; poison messages eventually dead-letter (TECH-NOTES §3.6).
/// In a high-scale deployment this worker is split into its own pod set and scaled on queue
/// depth (KEDA).
/// </summary>
public sealed class UnderwritingWorker : BackgroundService
{
    private readonly IServiceProvider _serviceProvider;
    private readonly ServiceBusOptions _options;
    private readonly ILogger<UnderwritingWorker> _logger;
    private ServiceBusProcessor? _processor;

    public UnderwritingWorker(
        IServiceProvider serviceProvider,
        IOptions<ServiceBusOptions> options,
        ILogger<UnderwritingWorker> logger)
    {
        _serviceProvider = serviceProvider;
        _options = options.Value;
        _logger = logger;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        if (string.IsNullOrWhiteSpace(_options.ConnectionString))
        {
            _logger.LogWarning("Service Bus not configured; underwriting worker is idle.");
            return;
        }

        var client = _serviceProvider.GetRequiredService<ServiceBusClient>();
        _processor = client.CreateProcessor(_options.UnderwritingQueue, new ServiceBusProcessorOptions
        {
            MaxConcurrentCalls = 5,
            AutoCompleteMessages = false
        });

        _processor.ProcessMessageAsync += OnMessageAsync;
        _processor.ProcessErrorAsync += OnErrorAsync;

        await _processor.StartProcessingAsync(stoppingToken);
        _logger.LogInformation("Underwriting worker listening on '{Queue}'.", _options.UnderwritingQueue);

        try
        {
            await Task.Delay(Timeout.Infinite, stoppingToken);
        }
        catch (OperationCanceledException) { /* shutting down */ }
        finally
        {
            await _processor.StopProcessingAsync(CancellationToken.None);
            await _processor.DisposeAsync();
        }
    }

    private async Task OnMessageAsync(ProcessMessageEventArgs args)
    {
        var message = args.Message.Body.ToObjectFromJson<UnderwriteInvoiceMessage>();

        // Each message gets its own DI scope (and DbContext) — the singleton worker must
        // never share a scoped DbContext across concurrent messages (TECH-NOTES §3.6).
        await using var scope = _serviceProvider.CreateAsyncScope();
        var mediator = scope.ServiceProvider.GetRequiredService<ISender>();

        try
        {
            await mediator.Send(new AssessInvoiceCommand(message.InvoiceId), args.CancellationToken);
            await args.CompleteMessageAsync(args.Message, args.CancellationToken);
        }
        catch (Exception ex)
        {
            _logger.LogError(ex, "Underwriting failed for invoice {InvoiceId}; abandoning for retry.",
                message.InvoiceId);
            await args.AbandonMessageAsync(args.Message, cancellationToken: args.CancellationToken);
        }
    }

    private Task OnErrorAsync(ProcessErrorEventArgs args)
    {
        _logger.LogError(args.Exception, "Service Bus processor error in {Source}.", args.ErrorSource);
        return Task.CompletedTask;
    }
}
