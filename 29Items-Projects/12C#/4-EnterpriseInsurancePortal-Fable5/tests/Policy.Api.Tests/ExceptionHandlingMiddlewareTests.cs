using System.Text.Json;
using FluentAssertions;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.Logging.Abstractions;
using Policy.Api.Middleware;
using Policy.Api.Rating;
using Policy.Domain.Exceptions;
using Xunit;

namespace Policy.Api.Tests;

public class ExceptionHandlingMiddlewareTests
{
    private static async Task<(int Status, string Body)> RunAsync(Exception? toThrow)
    {
        var middleware = new ExceptionHandlingMiddleware(
            _ => toThrow is null ? Task.CompletedTask : throw toThrow,
            NullLogger<ExceptionHandlingMiddleware>.Instance);

        var context = new DefaultHttpContext();
        context.Response.Body = new MemoryStream();

        await middleware.InvokeAsync(context);

        context.Response.Body.Position = 0;
        var body = await new StreamReader(context.Response.Body).ReadToEndAsync();
        return (context.Response.StatusCode, body);
    }

    [Fact]
    public async Task NoException_PassesThrough()
    {
        var (status, body) = await RunAsync(null);

        status.Should().Be(StatusCodes.Status200OK);
        body.Should().BeEmpty();
    }

    [Fact]
    public async Task EntityNotFound_Maps404_WithProblemDetails()
    {
        var (status, body) = await RunAsync(new EntityNotFoundException("Quote", Guid.Empty));

        status.Should().Be(StatusCodes.Status404NotFound);
        var problem = JsonSerializer.Deserialize<JsonElement>(body);
        problem.GetProperty("title").GetString().Should().Be("Not found");
        problem.GetProperty("detail").GetString().Should().Contain("Quote");
    }

    [Theory]
    [InlineData(typeof(DomainException))]
    [InlineData(typeof(InvalidStateTransitionException))]
    public async Task DomainExceptions_Map422(Type exceptionType)
    {
        var exception = (Exception)Activator.CreateInstance(exceptionType, "rule broken")!;

        var (status, body) = await RunAsync(exception);

        status.Should().Be(StatusCodes.Status422UnprocessableEntity);
        body.Should().Contain("rule broken");
    }

    [Fact]
    public async Task RatingUnavailable_Maps503_WithoutLeakingDetails()
    {
        var (status, body) = await RunAsync(new RatingUnavailableException("internal grpc address xyz"));

        status.Should().Be(StatusCodes.Status503ServiceUnavailable);
        body.Should().NotContain("xyz", "internal failure details must not leak to clients");
        body.Should().Contain("rating service");
    }

    [Fact]
    public async Task UnexpectedException_Maps500_Opaquely()
    {
        var (status, body) = await RunAsync(new InvalidOperationException("connection string = secret"));

        status.Should().Be(StatusCodes.Status500InternalServerError);
        body.Should().NotContain("secret");
        body.Should().Contain("unexpected error");
    }
}
