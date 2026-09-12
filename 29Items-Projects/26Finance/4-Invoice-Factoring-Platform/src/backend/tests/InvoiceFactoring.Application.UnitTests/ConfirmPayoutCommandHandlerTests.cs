using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Features.Payments.Commands.ConfirmPayout;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.ValueObjects;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class ConfirmPayoutCommandHandlerTests
{
    private readonly IAdvanceRepository _advances = Substitute.For<IAdvanceRepository>();
    private readonly IInvoiceRepository _invoices = Substitute.For<IInvoiceRepository>();
    private readonly IUnitOfWork _unitOfWork = Substitute.For<IUnitOfWork>();

    private ConfirmPayoutCommandHandler CreateHandler() =>
        new(_advances, _invoices, _unitOfWork, NullLogger<ConfirmPayoutCommandHandler>.Instance);

    private static (Invoice Invoice, Advance Advance) DisbursingPair()
    {
        var invoice = Invoice.Submit(
            Guid.NewGuid(), "Acme", "12-3456789", new Money(10_000m),
            new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.05, "test", "[]"));
        invoice.AcceptOffer(); // -> Disbursing
        var advance = Advance.Create(invoice);
        advance.AttachPayout("po_1");
        return (invoice, advance);
    }

    [Fact]
    public async Task Handle_SuccessfulPayout_MarksDisbursedAndOutstanding()
    {
        var (invoice, advance) = DisbursingPair();
        _advances.GetByStripePayoutIdAsync("po_1", Arg.Any<CancellationToken>()).Returns(advance);
        _invoices.GetByIdAsync(advance.InvoiceId, Arg.Any<CancellationToken>()).Returns(invoice);

        await CreateHandler().Handle(new ConfirmPayoutCommand("po_1", Succeeded: true), CancellationToken.None);

        advance.Status.Should().Be(AdvanceStatus.Disbursed);
        invoice.Status.Should().Be(InvoiceStatus.Outstanding);
        await _unitOfWork.Received(1).SaveChangesAsync(Arg.Any<CancellationToken>());
    }

    [Fact]
    public async Task Handle_UnknownPayout_IsIdempotentNoOp()
    {
        _advances.GetByStripePayoutIdAsync("po_x", Arg.Any<CancellationToken>()).Returns((Advance?)null);

        await CreateHandler().Handle(new ConfirmPayoutCommand("po_x", Succeeded: true), CancellationToken.None);

        await _unitOfWork.DidNotReceive().SaveChangesAsync(Arg.Any<CancellationToken>());
    }
}
