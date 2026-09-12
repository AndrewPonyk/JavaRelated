using System.Text.Json;
using Microsoft.EntityFrameworkCore;
using Policy.Api.Auth;
using Policy.Api.Models;
using Policy.Api.Rating;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Policy.Infrastructure;

namespace Policy.Api.Services;

public class QuoteService(
    IDbContextFactory<PolicyDbContext> dbFactory,
    IRatingClient ratingClient,
    ICurrentUser currentUser,
    ILogger<QuoteService> logger) : IQuoteService
{
    public async Task<PagedResult<QuoteDto>> ListAsync(string? brokerId, Paging paging, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var query = db.Quotes.AsNoTracking()
            .Where(q => brokerId == null || q.BrokerId == brokerId);
        var total = await query.CountAsync(ct);
        var items = await query
            .OrderByDescending(q => q.IssuedAtUtc).ThenBy(q => q.Id)
            .Skip(paging.Skip).Take(paging.PageSize)
            .Select(q => ToDto(q))
            .ToListAsync(ct);
        return new PagedResult<QuoteDto>(items, total);
    }

    public async Task<QuoteDto?> GetByIdAsync(Guid id, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var quote = await db.Quotes.AsNoTracking().FirstOrDefaultAsync(q => q.Id == id, ct);
        return quote is null ? null : ToDto(quote);
    }

    public async Task<QuoteDto> CreateAsync(CreateQuoteRequest request, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        if (!await db.Customers.AnyAsync(c => c.Id == request.CustomerId, ct))
        {
            throw new EntityNotFoundException(nameof(Customer), request.CustomerId);
        }

        var riskFactors = request.RiskFactors ?? [];
        var rating = await ratingClient.RateAsync(request.ProductCode, request.StateCode, riskFactors, ct);

        var quote = Quote.Issue(
            request.CustomerId,
            request.BrokerId,
            request.ProductCode,
            request.StateCode,
            JsonSerializer.Serialize(riskFactors),
            rating.AnnualPremium,
            rating.RateTableVersion,
            DateTime.UtcNow);

        db.Quotes.Add(quote);
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "QuoteIssued",
            EntityName = nameof(Quote),
            EntityId = quote.Id,
            Actor = currentUser.Name,
            Details = $"premium={quote.Premium:0.00};rateTable={quote.RateTableVersion}",
            OccurredAtUtc = DateTime.UtcNow,
        });
        await db.SaveChangesAsync(ct);

        logger.LogInformation("Quote {QuoteId} issued for {Premium} ({Product}/{State})",
            quote.Id, quote.Premium, quote.ProductCode, quote.StateCode);
        return ToDto(quote);
    }

    public async Task<QuoteDto> DeclineAsync(Guid id, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var quote = await db.Quotes.FirstOrDefaultAsync(q => q.Id == id, ct)
            ?? throw new EntityNotFoundException(nameof(Quote), id);

        quote.Decline();
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "QuoteDeclined",
            EntityName = nameof(Quote),
            EntityId = quote.Id,
            Actor = currentUser.Name,
            OccurredAtUtc = DateTime.UtcNow,
        });
        await db.SaveChangesAsync(ct);
        return ToDto(quote);
    }

    private static QuoteDto ToDto(Quote q) => new(
        q.Id, q.CustomerId, q.BrokerId, q.ProductCode, q.StateCode,
        q.Premium, q.RateTableVersion, q.Status.ToString(), q.IssuedAtUtc, q.ExpiresAtUtc);
}
