using InvoiceFactoring.Domain.Entities;

namespace InvoiceFactoring.Application.Abstractions.Persistence;

public interface IInvoiceRepository
{
    Task<Invoice?> GetByIdAsync(Guid id, CancellationToken ct = default);

    /// <summary>Loads the invoice together with its assessment (for offer/funding flows).</summary>
    Task<Invoice?> GetWithAssessmentAsync(Guid id, CancellationToken ct = default);

    /// <summary>Returns one page of a company's invoices (newest first) plus the total count.</summary>
    Task<(IReadOnlyList<Invoice> Items, int TotalCount)> ListByCompanyAsync(
        Guid companyId, int page, int pageSize, CancellationToken ct = default);

    Task AddAsync(Invoice invoice, CancellationToken ct = default);

    void Update(Invoice invoice);
}
