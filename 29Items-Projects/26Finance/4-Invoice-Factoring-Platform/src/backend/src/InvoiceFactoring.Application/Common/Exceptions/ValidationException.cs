using FluentValidation.Results;

namespace InvoiceFactoring.Application.Common.Exceptions;

/// <summary>
/// Aggregates FluentValidation failures from the pipeline. Maps to HTTP 400 with a
/// per-field error dictionary (RFC 7807 ValidationProblemDetails) at the edge.
/// </summary>
public sealed class ValidationException : Exception
{
    public IReadOnlyDictionary<string, string[]> Errors { get; }

    public ValidationException(IEnumerable<ValidationFailure> failures)
        : base("One or more validation failures occurred.")
    {
        Errors = failures
            .GroupBy(f => f.PropertyName)
            .ToDictionary(g => g.Key, g => g.Select(f => f.ErrorMessage).ToArray());
    }
}
