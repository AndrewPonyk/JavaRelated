namespace InvoiceFactoring.Application.Common.Exceptions;

/// <summary>Requested aggregate does not exist. Maps to HTTP 404 at the edge.</summary>
public sealed class NotFoundException : Exception
{
    public NotFoundException(string entity, object key)
        : base($"{entity} with key '{key}' was not found.") { }
}
