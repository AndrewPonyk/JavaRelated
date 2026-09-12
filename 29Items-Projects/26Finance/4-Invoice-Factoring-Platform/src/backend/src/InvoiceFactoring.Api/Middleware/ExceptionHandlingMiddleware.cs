using System.Diagnostics;
using InvoiceFactoring.Application.Common.Exceptions;
using InvoiceFactoring.Domain.Exceptions;
using Microsoft.AspNetCore.Mvc;

namespace InvoiceFactoring.Api.Middleware;

/// <summary>
/// Single place that turns any unhandled exception into an RFC 7807 ProblemDetails
/// response with a stable trace id. Stack traces are never leaked outside Development
/// (ARCHITECTURE §2.6).
/// </summary>
public sealed class ExceptionHandlingMiddleware
{
    private readonly RequestDelegate _next;
    private readonly ILogger<ExceptionHandlingMiddleware> _logger;
    private readonly IHostEnvironment _env;

    public ExceptionHandlingMiddleware(
        RequestDelegate next,
        ILogger<ExceptionHandlingMiddleware> logger,
        IHostEnvironment env)
    {
        _next = next;
        _logger = logger;
        _env = env;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await _next(context);
        }
        catch (Exception ex)
        {
            await WriteProblemAsync(context, ex);
        }
    }

    private async Task WriteProblemAsync(HttpContext context, Exception exception)
    {
        var traceId = Activity.Current?.Id ?? context.TraceIdentifier;

        var (status, title) = exception switch
        {
            ValidationException => (StatusCodes.Status400BadRequest, "Validation failed"),
            NotFoundException => (StatusCodes.Status404NotFound, "Resource not found"),
            DomainException => (StatusCodes.Status409Conflict, "Business rule violation"),
            _ => (StatusCodes.Status500InternalServerError, "An unexpected error occurred")
        };

        // 5xx is our fault and unexpected → log at Error. 4xx is the caller's input → Warning.
        if (status >= 500)
            _logger.LogError(exception, "Unhandled exception. TraceId={TraceId}", traceId);
        else
            _logger.LogWarning("Handled {Type}: {Message}. TraceId={TraceId}",
                exception.GetType().Name, exception.Message, traceId);

        var problem = new ProblemDetails
        {
            Status = status,
            Title = title,
            Type = $"https://httpstatuses.io/{status}",
            Detail = status >= 500 && !_env.IsDevelopment() ? null : exception.Message
        };
        problem.Extensions["traceId"] = traceId;

        if (exception is ValidationException validation)
            problem.Extensions["errors"] = validation.Errors;

        context.Response.StatusCode = status;
        context.Response.ContentType = "application/problem+json";
        await context.Response.WriteAsJsonAsync(problem);
    }
}
