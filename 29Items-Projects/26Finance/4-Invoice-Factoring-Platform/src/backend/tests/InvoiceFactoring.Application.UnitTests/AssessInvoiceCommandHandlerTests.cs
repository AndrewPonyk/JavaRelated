using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Banking;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Abstractions.Scoring;
using InvoiceFactoring.Application.Features.Underwriting.Commands.AssessInvoice;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.ValueObjects;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class AssessInvoiceCommandHandlerTests
{
    private readonly IInvoiceRepository _invoices = Substitute.For<IInvoiceRepository>();
    private readonly ICompanyRepository _companies = Substitute.For<ICompanyRepository>();
    private readonly IBankDataProvider _bankData = Substitute.For<IBankDataProvider>();
    private readonly ICreditScoringService _scoring = Substitute.For<ICreditScoringService>();
    private readonly IUnitOfWork _unitOfWork = Substitute.For<IUnitOfWork>();

    private AssessInvoiceCommandHandler CreateHandler() =>
        new(_invoices, _companies, _bankData, _scoring, _unitOfWork,
            NullLogger<AssessInvoiceCommandHandler>.Instance);

    private static Invoice NewInvoice() => Invoice.Submit(
        Guid.NewGuid(), "Acme Corp", "12-3456789", new Money(10_000m),
        new DateOnly(2026, 1, 1), new DateOnly(2026, 3, 1));

    private void Arrange(Invoice invoice, Company company, double pd)
    {
        _invoices.GetWithAssessmentAsync(invoice.Id, Arg.Any<CancellationToken>()).Returns(invoice);
        _companies.GetByIdAsync(invoice.CompanyId, Arg.Any<CancellationToken>()).Returns(company);
        _bankData.GetCashFlowAsync(Arg.Any<string>(), Arg.Any<CancellationToken>())
            .Returns(new BankCashFlowSnapshot(50_000, 30_000, 20_000, 36));
        _scoring.ScoreAsync(Arg.Any<CreditScoringRequest>(), Arg.Any<CancellationToken>())
            .Returns(new CreditScoringResult(pd, "test", new List<FeatureContribution>()));
    }

    [Fact]
    public async Task Handle_LowRisk_ApprovesAndPersists()
    {
        var invoice = NewInvoice();
        var company = Company.Register("Acme", "12-3456789", "a@b.com");
        company.LinkBankAccount("item_1");
        Arrange(invoice, company, pd: 0.05);

        await CreateHandler().Handle(new AssessInvoiceCommand(invoice.Id), CancellationToken.None);

        invoice.Status.Should().Be(InvoiceStatus.Approved);
        invoice.Assessment.Should().NotBeNull();
        await _bankData.Received(1).GetCashFlowAsync("item_1", Arg.Any<CancellationToken>());
        await _unitOfWork.Received(1).SaveChangesAsync(Arg.Any<CancellationToken>());
    }

    [Fact]
    public async Task Handle_HighRisk_Declines()
    {
        var invoice = NewInvoice();
        var company = Company.Register("Acme", "12-3456789", "a@b.com");
        Arrange(invoice, company, pd: 0.95);

        await CreateHandler().Handle(new AssessInvoiceCommand(invoice.Id), CancellationToken.None);

        invoice.Status.Should().Be(InvoiceStatus.Declined);
    }

    [Fact]
    public async Task Handle_AlreadyAssessed_IsIdempotentNoOp()
    {
        var invoice = NewInvoice();
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.05, "prior", "[]"));
        _invoices.GetWithAssessmentAsync(invoice.Id, Arg.Any<CancellationToken>()).Returns(invoice);

        await CreateHandler().Handle(new AssessInvoiceCommand(invoice.Id), CancellationToken.None);

        await _scoring.DidNotReceive().ScoreAsync(Arg.Any<CreditScoringRequest>(), Arg.Any<CancellationToken>());
        await _unitOfWork.DidNotReceive().SaveChangesAsync(Arg.Any<CancellationToken>());
    }
}
