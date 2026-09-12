using FluentAssertions;
using InvoiceFactoring.Domain.Entities;
using InvoiceFactoring.Domain.Enums;
using InvoiceFactoring.Domain.Events;
using InvoiceFactoring.Domain.Exceptions;
using InvoiceFactoring.Domain.ValueObjects;
using Xunit;

namespace InvoiceFactoring.Domain.UnitTests;

public class InvoiceTests
{
    private static Invoice ValidInvoice() => Invoice.Submit(
        companyId: Guid.NewGuid(),
        debtorName: "Acme Corp",
        debtorTaxId: "12-3456789",
        faceValue: new Money(10_000m),
        issueDate: new DateOnly(2026, 1, 1),
        dueDate: new DateOnly(2026, 3, 1));

    [Fact]
    public void Submit_WithValidData_StartsInSubmittedStatusAndRaisesEvent()
    {
        var invoice = ValidInvoice();

        invoice.Status.Should().Be(InvoiceStatus.Submitted);
        invoice.DomainEvents.Should().ContainSingle(e => e is InvoiceSubmittedEvent);
    }

    [Fact]
    public void Submit_WithDueDateBeforeIssueDate_Throws()
    {
        var act = () => Invoice.Submit(
            Guid.NewGuid(), "Acme", "12-3456789", new Money(1_000m),
            new DateOnly(2026, 3, 1), new DateOnly(2026, 1, 1));

        act.Should().Throw<DomainException>().WithMessage("*Due date*");
    }

    [Fact]
    public void Submit_WithNonPositiveAmount_Throws()
    {
        var act = () => Invoice.Submit(
            Guid.NewGuid(), "Acme", "12-3456789", new Money(0m),
            new DateOnly(2026, 1, 1), new DateOnly(2026, 2, 1));

        act.Should().Throw<DomainException>().WithMessage("*positive*");
    }

    [Fact]
    public void ApplyAssessment_WhenApproved_MovesToApproved()
    {
        var invoice = ValidInvoice();
        var assessment = CreditAssessment.FromScore(invoice.Id, probabilityOfDefault: 0.05,
            modelVersion: "test", reasonCodesJson: "[]");

        invoice.ApplyAssessment(assessment);

        invoice.Status.Should().Be(InvoiceStatus.Approved);
        invoice.Assessment.Should().Be(assessment);
    }

    [Fact]
    public void ApplyAssessment_WhenRiskTooHigh_MovesToDeclined()
    {
        var invoice = ValidInvoice();
        var assessment = CreditAssessment.FromScore(invoice.Id, probabilityOfDefault: 0.95,
            modelVersion: "test", reasonCodesJson: "[]");

        invoice.ApplyAssessment(assessment);

        invoice.Status.Should().Be(InvoiceStatus.Declined);
        invoice.DeclineReason.Should().NotBeNull();
    }

    [Fact]
    public void AcceptOffer_BeforeApproval_Throws()
    {
        var invoice = ValidInvoice();

        var act = () => invoice.AcceptOffer();

        act.Should().Throw<DomainException>();
    }

    [Fact]
    public void FullHappyPath_TransitionsThroughToRepaid()
    {
        var invoice = ValidInvoice();
        invoice.ApplyAssessment(CreditAssessment.FromScore(invoice.Id, 0.05, "test", "[]"));

        invoice.AcceptOffer();
        invoice.Status.Should().Be(InvoiceStatus.Disbursing);

        invoice.MarkOutstanding();
        invoice.Status.Should().Be(InvoiceStatus.Outstanding);

        invoice.MarkRepaid();
        invoice.Status.Should().Be(InvoiceStatus.Repaid);
    }
}
