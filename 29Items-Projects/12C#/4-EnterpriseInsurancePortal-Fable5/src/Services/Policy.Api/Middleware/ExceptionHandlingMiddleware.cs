using Microsoft.AspNetCore.Mvc;
using Policy.Api.Rating;
using Policy.Domain.Exceptions;

namespace Policy.Api.Middleware;

/// <summary>
/// Single exception boundary: domain exceptions become RFC 7807 ProblemDetails,
/// everything else becomes an opaque 500 (details stay in the logs). ARCHITECTURE.md §2.6.
/// </summary>
public class ExceptionHandlingMiddleware(RequestDelegate next, ILogger<ExceptionHandlingMiddleware> logger)
{
    public async Task InvokeAsync(HttpContext context)
    {
        try
        {
            await next(context);
        }
        catch (EntityNotFoundException ex)
        {
            await WriteProblem(context, StatusCodes.Status404NotFound, "Not found", ex.Message);
        }
        catch (DomainException ex)
        {
            await WriteProblem(context, StatusCodes.Status422UnprocessableEntity, "Business rule violation", ex.Message);
        }
        catch (RatingUnavailableException ex)
        {
            logger.LogError(ex, "Rating service unavailable");
            await WriteProblem(context, StatusCodes.Status503ServiceUnavailable, "Dependency unavailable",
                "The rating service is currently unavailable. Please retry shortly.");
        }
        catch (OperationCanceledException) when (context.RequestAborted.IsCancellationRequested)
        {
            // Client disconnected — nothing to write.
        }
        catch (Exception ex)
        {
            logger.LogError(ex, "Unhandled exception for {Method} {Path}", context.Request.Method, context.Request.Path);
            await WriteProblem(context, StatusCodes.Status500InternalServerError, "Internal server error",
                "An unexpected error occurred.");
        }
    }

    private static async Task WriteProblem(HttpContext context, int status, string title, string detail)
    {
        if (context.Response.HasStarted)
        {
            return;
        }

        context.Response.StatusCode = status;
        context.Response.ContentType = "application/problem+json";
        await context.Response.WriteAsJsonAsync(new ProblemDetails
        {
            Status = status,
            Title = title,
            Detail = detail,
            Instance = context.Request.Path,
        });
    }
}
