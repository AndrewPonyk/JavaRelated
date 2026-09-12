using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Payments;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Features.Advances.Commands.AcceptOffer;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.Exceptions;
using InvoiceFactoring.Domain.ValueObjects;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class AcceptAdvanceOfferCommandHandlerTests
{
    private readonly IInvoiceRepository _invoices = Substitute.For<IInvoiceRepository>();
    private readonly ICompanyRepository _companies = Substitute.For<ICompanyRepository>();
    private readonly IAdvanceRepository _advances = Substitute.For<IAdvanceRepository>();
    private readonly IPaymentGateway _payments = Substitute.For<IPaymentGateway>();
    private readonly IUnitOfWork _unitOfWork = Substitute.For<IUnitOfWork>();

    private AcceptAdvanceOfferCommandHandler CreateHandler() =>
        new(_invoices, _companies, _advances, _payments, _unitOfWork,
            NullLogger<AcceptAdvanceOfferCommandHandler>.Instance);

    private static Invoice ApprovedInvoice()
    {
        var invoice = Invoice.Submit(
            Guid.NewGuid(), "Acme", "12-3456789", new Money(10_000m),
            new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.05, "test", "[]"));
        return invoice;
    }

    [Fact]
    public async Task Handle_DisbursesAdvanceWithIdempotencyKey()
    {
        var invoice = ApprovedInvoice();
        var company = Company.Register("Acme", "12-3456789", "a@b.com");
        company.LinkStripeAccount("acct_1");
        _invoices.GetWithAssessmentAsync(invoice.Id, Arg.Any<CancellationToken>()).Returns(invoice);
        _companies.GetByIdAsync(invoice.CompanyId, Arg.Any<CancellationToken>()).Returns(company);
        _payments.CreatePayoutAsync(Arg.Any<string>(), Arg.Any<Money>(), Arg.Any<string>(), Arg.Any<CancellationToken>())
            .Returns(new PayoutResult("po_1", "pending"));

        var result = await CreateHandler().Handle(
            new AcceptAdvanceOfferCommand(invoice.Id, "idem-1"), CancellationToken.None);

        result.NetDisbursed.Should().BeGreaterThan(0);
        result.StripePayoutId.Should().Be("po_1");
        invoice.Status.Should().Be(InvoiceStatus.Disbursing);
        await _payments.Received(1).CreatePayoutAsync("acct_1", Arg.Any<Money>(), "idem-1", Arg.Any<CancellationToken>());
        await _advances.Received(1).AddAsync(Arg.Any<Advance>(), Arg.Any<CancellationToken>());
        await _unitOfWork.Received(1).SaveChangesAsync(Arg.Any<CancellationToken>());
    }

    [Fact]
    public async Task Handle_NoConnectedAccount_Throws()
    {
        var invoice = ApprovedInvoice();
        var company = Company.Register("Acme", "12-3456789", "a@b.com"); // no Stripe account linked
        _invoices.GetWithAssessmentAsync(invoice.Id, Arg.Any<CancellationToken>()).Returns(invoice);
        _companies.GetByIdAsync(invoice.CompanyId, Arg.Any<CancellationToken>()).Returns(company);

        var act = () => CreateHandler().Handle(
            new AcceptAdvanceOfferCommand(invoice.Id, "idem-1"), CancellationToken.None);

        await act.Should().ThrowAsync<DomainException>();
        await _payments.DidNotReceive().CreatePayoutAsync(
            Arg.Any<string>(), Arg.Any<Money>(), Arg.Any<string>(), Arg.Any<CancellationToken>());
    }
}
