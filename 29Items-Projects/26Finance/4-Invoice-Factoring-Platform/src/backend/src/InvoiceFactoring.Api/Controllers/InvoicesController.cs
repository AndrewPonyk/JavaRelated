using InvoiceFactoring.Application.Common.Models;
using InvoiceFactoring.Application.Features.Advances.Commands.AcceptOffer;
using InvoiceFactoring.Application.Features.Advances.Dtos;
using InvoiceFactoring.Application.Features.Invoices.Commands.SubmitInvoice;
using InvoiceFactoring.Application.Features.Invoices.Dtos;
using InvoiceFactoring.Application.Features.Invoices.Queries.GetInvoiceById;
using InvoiceFactoring.Application.Features.Invoices.Queries.ListCompanyInvoices;
using MediatR;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace InvoiceFactoring.Api.Controllers;

/// <summary>
/// Invoice factoring endpoints. Thin controller — it only translates HTTP to MediatR
/// requests; business logic lives in the Application handlers, validation in the pipeline.
/// </summary>
[ApiController]
[Route("api/invoices")]
[Authorize(Policy = "Borrower")]
[Produces("application/json")]
public sealed class InvoicesController : ControllerBase
{
    private readonly ISender _mediator;

    public InvoicesController(ISender mediator) => _mediator = mediator;

    /// <summary>Submit a new invoice for factoring. Underwriting then runs asynchronously.</summary>
    [HttpPost]
    [ProducesResponseType(typeof(SubmitInvoiceResponse), StatusCodes.Status202Accepted)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    public async Task<IActionResult> Submit(
        [FromBody] SubmitInvoiceRequest request,
        CancellationToken ct)
    {
        // TODO: derive CompanyId from the authenticated principal's claims instead of the body.
        var command = new SubmitInvoiceCommand(
            request.CompanyId,
            request.DebtorName,
            request.DebtorTaxId,
            request.Amount,
            request.Currency,
            request.IssueDate,
            request.DueDate,
            request.DocumentUri);

        var invoiceId = await _mediator.Send(command, ct);

        // 202: accepted for async underwriting; poll GET to see the offer.
        return AcceptedAtAction(nameof(GetById), new { id = invoiceId },
            new SubmitInvoiceResponse(invoiceId, "Submitted"));
    }

    /// <summary>List the authenticated company's invoices (most recent first), paged.</summary>
    [HttpGet]
    [ProducesResponseType(typeof(PagedResult<InvoiceDto>), StatusCodes.Status200OK)]
    public async Task<ActionResult<PagedResult<InvoiceDto>>> List(
        [FromQuery] Guid companyId,
        [FromQuery] int page = 1,
        [FromQuery] int pageSize = 20,
        CancellationToken ct = default)
    {
        // TODO: derive companyId from the authenticated principal rather than the query string.
        var result = await _mediator.Send(new ListCompanyInvoicesQuery(companyId, page, pageSize), ct);
        return Ok(result);
    }

    /// <summary>Get an invoice with its underwriting offer (once available).</summary>
    [HttpGet("{id:guid}")]
    [ProducesResponseType(typeof(InvoiceDto), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<InvoiceDto>> GetById(Guid id, CancellationToken ct)
        => Ok(await _mediator.Send(new GetInvoiceByIdQuery(id), ct));

    /// <summary>Accept the advance offer for an approved invoice → disburse funds.</summary>
    [HttpPost("{id:guid}/accept-offer")]
    [ProducesResponseType(typeof(AdvanceDto), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status409Conflict)]
    public async Task<ActionResult<AdvanceDto>> AcceptOffer(
        Guid id,
        [FromHeader(Name = "Idempotency-Key")] string? idempotencyKey,
        CancellationToken ct)
    {
        // Idempotency key prevents double-disbursement on retries (TECH-NOTES §3.6).
        var key = string.IsNullOrWhiteSpace(idempotencyKey) ? Guid.NewGuid().ToString() : idempotencyKey;
        var advance = await _mediator.Send(new AcceptAdvanceOfferCommand(id, key), ct);
        return Ok(advance);
    }
}

// ── API request/response contracts (decoupled from the internal commands) ────────────
public sealed record SubmitInvoiceRequest(
    Guid CompanyId,
    string DebtorName,
    string DebtorTaxId,
    decimal Amount,
    string Currency,
    DateOnly IssueDate,
    DateOnly DueDate,
    string? DocumentUri);

public sealed record SubmitInvoiceResponse(Guid InvoiceId, string Status);
