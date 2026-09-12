using Microsoft.EntityFrameworkCore;
using Policy.Api.Auth;
using Policy.Api.Models;
using Policy.Domain.Entities;
using Policy.Domain.Exceptions;
using Policy.Infrastructure;

namespace Policy.Api.Services;

public class CustomerService(
    IDbContextFactory<PolicyDbContext> dbFactory,
    ICurrentUser currentUser) : ICustomerService
{
    public async Task<PagedResult<CustomerDto>> ListAsync(Paging paging, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        var query = db.Customers.AsNoTracking();
        var total = await query.CountAsync(ct);
        var items = await query
            .OrderBy(c => c.LastName).ThenBy(c => c.FirstName).ThenBy(c => c.Id)
            .Skip(paging.Skip).Take(paging.PageSize)
            .Select(c => new CustomerDto(c.Id, c.FirstName, c.LastName, c.Email, c.DateOfBirth))
            .ToListAsync(ct);
        return new PagedResult<CustomerDto>(items, total);
    }

    public async Task<CustomerDto?> GetByIdAsync(Guid id, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);
        return await db.Customers.AsNoTracking()
            .Where(c => c.Id == id)
            .Select(c => new CustomerDto(c.Id, c.FirstName, c.LastName, c.Email, c.DateOfBirth))
            .FirstOrDefaultAsync(ct);
    }

    public async Task<CustomerDto> CreateAsync(CreateCustomerRequest request, CancellationToken ct)
    {
        await using var db = await dbFactory.CreateDbContextAsync(ct);

        var email = request.Email.Trim().ToLowerInvariant();
        if (await db.Customers.AnyAsync(c => c.Email == email, ct))
        {
            throw new DomainException($"A customer with email '{email}' already exists.");
        }

        var customer = Customer.Create(
            request.FirstName, request.LastName, request.Email, request.DateOfBirth,
            DateOnly.FromDateTime(DateTime.UtcNow));

        db.Customers.Add(customer);
        db.AuditEntries.Add(new AuditEntry
        {
            Action = "CustomerCreated",
            EntityName = nameof(Customer),
            EntityId = customer.Id,
            Actor = currentUser.Name,
            OccurredAtUtc = DateTime.UtcNow,
        });
        await db.SaveChangesAsync(ct);

        return new CustomerDto(customer.Id, customer.FirstName, customer.LastName, customer.Email, customer.DateOfBirth);
    }
}
