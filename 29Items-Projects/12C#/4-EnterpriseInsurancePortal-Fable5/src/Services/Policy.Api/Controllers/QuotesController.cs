using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Policy.Api.Models;
using Policy.Api.Services;

namespace Policy.Api.Controllers;

[ApiController]
[Route("api/v1/quotes")]
[Authorize(Policy = "Broker")]
public class QuotesController(IQuoteService quoteService) : ControllerBase
{
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<QuoteDto>>> List(
        [FromQuery] string? brokerId,
        [FromQuery] int page = 1, [FromQuery] int pageSize = Paging.DefaultPageSize, CancellationToken ct = default)
    {
        var result = await quoteService.ListAsync(brokerId, Paging.Normalize(page, pageSize), ct);
        Response.Headers["X-Total-Count"] = result.TotalCount.ToString();
        return Ok(result.Items);
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<QuoteDto>> GetById(Guid id, CancellationToken ct)
    {
        var quote = await quoteService.GetByIdAsync(id, ct);
        return quote is null ? NotFound() : Ok(quote);
    }

    /// <summary>Rates the risk via Rating.Grpc and issues a quote valid for 30 days.</summary>
    [HttpPost]
    public async Task<ActionResult<QuoteDto>> Create([FromBody] CreateQuoteRequest request, CancellationToken ct)
    {
        var quote = await quoteService.CreateAsync(request, ct);
        return CreatedAtAction(nameof(GetById), new { id = quote.Id }, quote);
    }

    [HttpPost("{id:guid}/decline")]
    public async Task<ActionResult<QuoteDto>> Decline(Guid id, CancellationToken ct)
        => Ok(await quoteService.DeclineAsync(id, ct));
}
