namespace InvoiceFactoring.Application.Abstractions.Persistence;

/// <summary>
/// Commits a coherent set of changes in one transaction. Implemented by the EF Core
/// DbContext, which also dispatches domain events raised during the unit of work.
/// </summary>
public interface IUnitOfWork
{
    Task<int> SaveChangesAsync(CancellationToken ct = default);
}
