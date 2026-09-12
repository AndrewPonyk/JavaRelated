namespace InvoiceFactoring.Domain.Exceptions;

/// <summary>
/// Thrown when an operation would violate a domain invariant
/// (e.g. funding an invoice that is already funded). Maps to HTTP 409/422 at the edge.
/// </summary>
public class DomainException : Exception
{
    public DomainException(string message) : base(message) { }
}
