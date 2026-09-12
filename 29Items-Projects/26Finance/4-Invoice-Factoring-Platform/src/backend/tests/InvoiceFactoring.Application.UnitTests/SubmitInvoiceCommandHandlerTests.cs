using FluentAssertions;
using InvoiceFactoring.Application.Abstractions.Persistence;
using InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;
using InvoiceFactoring.Domain.Entities;
using Microsoft.Extensions.Logging.Abstractions;
using NSubstitute;
using Xunit;

namespace InvoiceFactoring.Application.UnitTests;

public class SubmitInvoiceCommandHandlerTests
{
    private readonly IInvoiceRepository _invoices = Substitute.For<IInvoiceRepository>();
    private readonly IUnitOfWork _unitOfWork = Substitute.For<IUnitOfWork>();

    private SubmitInvoiceCommandHandler CreateHandler() =>
        new(_invoices, _unitOfWork, NullLogger<SubmitInvoiceCommandHandler>.Instance);

    private static SubmitInvoiceCommand ValidCommand() => new(
        CompanyId: Guid.NewGuid(),
        DebtorName: "Acme Corp",
        DebtorTaxId: "12-3456789",
        Amount: 10_000m,
        Currency: "USD",
        IssueDate: new DateOnly(2026, 1, 1),
        DueDate: new DateOnly(2026, 3, 1),
        DocumentUri: null);

    [Fact]
    public async Task Handle_PersistsInvoiceAndCommits()
    {
        var handler = CreateHandler();

        var id = await handler.Handle(ValidCommand(), CancellationToken.None);

        id.Should().NotBe(Guid.Empty);
        await _invoices.Received(1).AddAsync(Arg.Is<Invoice>(i => i.Id == id), Arg.Any<CancellationToken>());
        await _unitOfWork.Received(1).SaveChangesAsync(Arg.Any<CancellationToken>());
    }

    [Fact]
    public async Task Handle_BuildsInvoiceInSubmittedStatus()
    {
        Invoice? captured = null;
        await _invoices.AddAsync(Arg.Do<Invoice>(i => captured = i), Arg.Any<CancellationToken>());
        var handler = CreateHandler();

        await handler.Handle(ValidCommand(), CancellationToken.None);

        captured.Should().NotBeNull();
        captured!.Status.Should().Be(Domain.Enums.InvoiceStatus.Submitted);
        captured.FaceValue.Amount.Should().Be(10_000m);
    }
}
