using InvoiceFactoring.Domain.Entities;

namespace InvoiceFactoring.Application.Abstractions.Persistence;

public interface IAdvanceRepository
{
    Task<Advance?> GetByIdAsync(Guid id, CancellationToken ct = default);
    Task<Advance?> GetByInvoiceIdAsync(Guid invoiceId, CancellationToken ct = default);
    Task<Advance?> GetByStripePayoutIdAsync(string payoutId, CancellationToken ct = default);
    Task AddAsync(Advance advance, CancellationToken ct = default);
    void Update(Advance advance);
}
