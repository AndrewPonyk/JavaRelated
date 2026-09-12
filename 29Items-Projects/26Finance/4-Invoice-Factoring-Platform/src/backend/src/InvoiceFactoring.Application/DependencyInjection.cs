using System.Reflection;
using FluentValidation;
using InvoiceFactoring.Application.Common.Behaviors;
using MediatR;
using Microsoft.Extensions.DependencyInjection;

namespace InvoiceFactoring.Application;

/// <summary>Registers the Application layer (MediatR handlers, validators, behaviors).</summary>
public static class DependencyInjection
{
    public static IServiceCollection AddApplication(this IServiceCollection services)
    {
        var assembly = Assembly.GetExecutingAssembly();

        services.AddMediatR(cfg =>
        {
            cfg.RegisterServicesFromAssembly(assembly);
            cfg.AddOpenBehavior(typeof(ValidationBehavior<,>));
            // TODO: add LoggingBehavior, UnhandledExceptionBehavior, PerformanceBehavior.
        });

        services.AddValidatorsFromAssembly(assembly);

        return services;
    }
}
