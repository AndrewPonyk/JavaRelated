using Azure.Messaging.ServiceBus;
using InvoiceFactoring.Application.Abstractions.Banking;
using InvoiceFactoring.Application.Abstractions.Messaging;
using InvoiceFactoring.Application.Abstractions.Payments;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Abstractions.Scoring;
using InvoiceFactoring.Infrastructure.Banking.Plaid;
using InvoiceFactoring.Infrastructure.Messaging.ServiceBus;
using InvoiceFactoring.Infrastructure.Payments.Stripe;
using InvoiceFactoring.Infrastructure.Persistence;
using InvoiceFactoring.Infrastructure.Persistence.Repositories;
using InvoiceFactoring.Infrastructure.Scoring;
using InvoiceFactoring.ML;
using InvoiceFactoring.ML.Models;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.ML;

namespace InvoiceFactoring.Infrastructure;

/// <summary>Wires the Infrastructure adapters to the Application ports.</summary>
public static class DependencyInjection
{
    public static IServiceCollection AddInfrastructure(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        AddPersistence(services, configuration);
        AddOptions(services, configuration);
        AddExternalServices(services, configuration);
        return services;
    }

    private static void AddPersistence(IServiceCollection services, IConfiguration configuration)
    {
        services.AddDbContext<AppDbContext>(options =>
            options.UseSqlServer(
                configuration.GetConnectionString("SqlServer"),
                sql => sql.EnableRetryOnFailure()));   // transient-fault resilience

        // The DbContext is the unit of work.
        services.AddScoped<IUnitOfWork>(sp => sp.GetRequiredService<AppDbContext>());
        services.AddScoped<IInvoiceRepository, InvoiceRepository>();
        services.AddScoped<ICompanyRepository, CompanyRepository>();
        services.AddScoped<IAdvanceRepository, AdvanceRepository>();
    }

    private static void AddOptions(IServiceCollection services, IConfiguration configuration)
    {
        services.Configure<StripeOptions>(configuration.GetSection(StripeOptions.SectionName));
        services.Configure<PlaidOptions>(configuration.GetSection(PlaidOptions.SectionName));
        services.Configure<MlScoringOptions>(configuration.GetSection(MlScoringOptions.SectionName));
        services.Configure<ServiceBusOptions>(configuration.GetSection(ServiceBusOptions.SectionName));
    }

    private static void AddExternalServices(IServiceCollection services, IConfiguration configuration)
    {
        services.AddScoped<IPaymentGateway, StripePaymentGateway>();
        services.AddScoped<IBankDataProvider, PlaidBankDataProvider>();

        // Credit scoring: prefer the trained ML.NET model when its artifact is present
        // (pooled, thread-safe, hot-reloaded). Otherwise fall back to the transparent
        // heuristic scorer so underwriting always works and startup never requires a binary.
        var modelPath = configuration.GetSection(MlScoringOptions.SectionName)["ModelPath"]
                        ?? "./models/credit-risk-v1.zip";
        if (File.Exists(modelPath))
        {
            services.AddPredictionEnginePool<CreditRiskInput, CreditRiskPrediction>()
                .FromFile(modelName: ModelInfo.Name, filePath: modelPath, watchForChanges: true);
            services.AddScoped<ICreditScoringService, MlNetCreditScoringService>();
        }
        else
        {
            services.AddScoped<ICreditScoringService, HeuristicCreditScoringService>();
        }

        // Service Bus client is expensive → singleton; the publisher caches senders.
        services.AddSingleton(_ => new ServiceBusClient(
            configuration.GetSection(ServiceBusOptions.SectionName)["ConnectionString"]));
        services.AddSingleton<IIntegrationEventPublisher, ServiceBusEventPublisher>();
    }
}
