using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Messaging;
using InvoiceFactoring.Application.Common.Behaviors;
using InvoiceFactoring.Application.Common.Events;
using InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;
using InvoiceFactoring.Application.Features.Invoices.Dtos;
using InvoiceFactoring.Application.Features.Underwriting.Contracts;
using InvoiceFactoring.Application.Features.Underwriting.EventHandlers;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Events;
using InvoiceFactoring.Domain.ValueObjects;
using MediatR;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Xunit;
using ValidationException = InvoiceFactoring.Application.Common.Exceptions.ValidationException;

namespace InvoiceFactoring.Application.UnitTests;

public class ApplicationBehaviorTests
{
    private static SubmitInvoiceCommand ValidCommand() => new(
        Guid.NewGuid(), "Acme", "12-3456789", 10_000m, "USD",
        new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1), null);

    [Fact]
    public async Task ValidationBehavior_InvalidRequest_ThrowsAndShortCircuits()
    {
        var behavior = new ValidationBehavior<SubmitInvoiceCommand, Guid>(
            new[] { new SubmitInvoiceCommandValidator() });
        var nextCalled = false;
        Task<Guid> Next() { nextCalled = true; return Task.FromResult(Guid.NewGuid()); }

        var act = () => behavior.Handle(
            ValidCommand() with { Amount = -1 }, Next, CancellationToken.None);

        await act.Should().ThrowAsync<ValidationException>();
        nextCalled.Should().BeFalse();
    }

    [Fact]
    public async Task ValidationBehavior_ValidRequest_CallsNext()
    {
        var behavior = new ValidationBehavior<SubmitInvoiceCommand, Guid>(
            new[] { new SubmitInvoiceCommandValidator() });
        var expected = Guid.NewGuid();

        var result = await behavior.Handle(ValidCommand(), () => Task.FromResult(expected), CancellationToken.None);

        result.Should().Be(expected);
    }

    [Fact]
    public async Task InvoiceSubmittedEventHandler_PublishesUnderwritingMessage()
    {
        var publisher = Substitute.For<IIntegrationEventPublisher>();
        var handler = new InvoiceSubmittedEventHandler(
            publisher, NullLogger<InvoiceSubmittedEventHandler>.Instance);
        var invoiceId = Guid.NewGuid();
        var companyId = Guid.NewGuid();

        await handler.Handle(
            new DomainEventNotification<InvoiceSubmittedEvent>(new InvoiceSubmittedEvent(invoiceId, companyId)),
            CancellationToken.None);

        await publisher.Received(1).PublishAsync(
            Arg.Is<UnderwriteInvoiceMessage>(m => m.InvoiceId == invoiceId && m.CompanyId == companyId),
            Arg.Any<CancellationToken>());
    }

    [Fact]
    public void InvoiceDto_ApprovedInvoice_IncludesOffer()
    {
        var invoice = Invoice.Submit(Guid.NewGuid(), "Acme", "12-3456789", new Money(10_000m),
            new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.05, "test", "[]"));

        var dto = InvoiceDto.FromEntity(invoice);

        dto.Status.Should().Be("Approved");
        dto.Offer.Should().NotBeNull();
        dto.Offer!.NetDisbursement.Should().BeGreaterThan(0);
    }

    [Fact]
    public void InvoiceDto_DeclinedInvoice_HasReasonAndNoOffer()
    {
        var invoice = Invoice.Submit(Guid.NewGuid(), "Acme", "12-3456789", new Money(10_000m),
            new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.95, "test", "[]"));

        var dto = InvoiceDto.FromEntity(invoice);

        dto.Status.Should().Be("Declined");
        dto.Offer.Should().BeNull();
        dto.DeclineReason.Should().NotBeNull();
    }
}
