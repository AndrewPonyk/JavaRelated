namespace Policy.Domain.Exceptions;

/// <summary>
/// A business-rule violation. Mapped to HTTP 422 by the API's exception middleware
/// (see ARCHITECTURE.md §2.6 — fail loud at boundaries, recover in middleware).
/// </summary>
public class DomainException(string message) : Exception(message);

/// <summary>Requested aggregate does not exist. Mapped to HTTP 404.</summary>
public class EntityNotFoundException(string entityName, object key)
    : Exception($"{entityName} '{key}' was not found.")
{
    public string EntityName { get; } = entityName;
    public object Key { get; } = key;
}

/// <summary>Illegal lifecycle transition (e.g. binding an expired quote). Mapped to HTTP 422.</summary>
public class InvalidStateTransitionException(string message) : DomainException(message);
