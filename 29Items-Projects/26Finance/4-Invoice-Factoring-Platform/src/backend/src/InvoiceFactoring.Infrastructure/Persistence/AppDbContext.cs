using System.Reflection;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Common.Events;
using InvoiceFactoring.Domain.Common;
using InvoiceFactoring.Domain.Entities;
using MediatR;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata;

namespace InvoiceFactoring.Infrastructure.Persistence;

/// <summary>
/// EF Core unit of work. After persisting changes it dispatches the domain events raised
/// by the aggregates (in-process via MediatR), which is how submission triggers underwriting.
/// </summary>
public sealed class AppDbContext : DbContext, IUnitOfWork
{
    private readonly IPublisher _publisher;

    public AppDbContext(DbContextOptions<AppDbContext> options, IPublisher publisher)
        : base(options) => _publisher = publisher;

    public DbSet<Company> Companies => Set<Company>();
    public DbSet<Invoice> Invoices => Set<Invoice>();
    public DbSet<Advance> Advances => Set<Advance>();
    public DbSet<CreditAssessment> CreditAssessments => Set<CreditAssessment>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.ApplyConfigurationsFromAssembly(Assembly.GetExecutingAssembly());

        // Aggregate Ids are assigned by the domain (Guid in the Entity constructor), not the
        // store. Marking them ValueGeneratedNever makes EF treat a new child reachable from a
        // tracked aggregate (e.g. a CreditAssessment on an Invoice) as an INSERT, rather than
        // mistaking its client-set key for an existing row and issuing a doomed UPDATE.
        foreach (var entityType in modelBuilder.Model.GetEntityTypes())
        {
            var id = entityType.FindProperty("Id");
            if (id is not null && id.ClrType == typeof(Guid))
                id.ValueGenerated = ValueGenerated.Never;
        }

        base.OnModelCreating(modelBuilder);
    }

    public override async Task<int> SaveChangesAsync(CancellationToken cancellationToken = default)
    {
        // Collect domain events from tracked aggregates before saving.
        var aggregates = ChangeTracker.Entries<AggregateRoot>()
            .Select(e => e.Entity)
            .Where(a => a.DomainEvents.Count > 0)
            .ToList();

        var domainEvents = aggregates.SelectMany(a => a.DomainEvents).ToList();

        var result = await base.SaveChangesAsync(cancellationToken);

        // Dispatch AFTER commit. TODO: replace with a transactional Outbox for at-least-once
        // delivery — dispatch-after-save can drop events if the process dies here (TECH-NOTES §3.6).
        foreach (var aggregate in aggregates)
            aggregate.ClearDomainEvents();

        foreach (var domainEvent in domainEvents)
        {
            var notificationType = typeof(DomainEventNotification<>).MakeGenericType(domainEvent.GetType());
            var notification = (INotification)Activator.CreateInstance(notificationType, domainEvent)!;
            await _publisher.Publish(notification, cancellationToken);
        }

        return result;
    }
}
