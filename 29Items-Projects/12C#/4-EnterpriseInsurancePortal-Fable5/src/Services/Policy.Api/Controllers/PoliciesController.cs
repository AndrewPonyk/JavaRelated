using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Policy.Api.Models;
using Policy.Api.Services;

namespace Policy.Api.Controllers;

[ApiController]
[Route("api/v1/policies")]
[Authorize]
public class PoliciesController(IPolicyService policyService) : ControllerBase
{
    /// <summary>Brokers see all (optionally filtered); customers are always scoped to their own.</summary>
    [HttpGet]
    public async Task<ActionResult<IReadOnlyList<PolicyDto>>> List(
        [FromQuery] Guid? customerId,
        [FromQuery] int page = 1, [FromQuery] int pageSize = Paging.DefaultPageSize, CancellationToken ct = default)
    {
        var result = await policyService.ListAsync(customerId, Paging.Normalize(page, pageSize), ct);
        Response.Headers["X-Total-Count"] = result.TotalCount.ToString();
        return Ok(result.Items);
    }

    [HttpGet("{id:guid}")]
    public async Task<ActionResult<PolicyDetailDto>> GetById(Guid id, CancellationToken ct)
    {
        var policy = await policyService.GetByIdAsync(id, ct);
        return policy is null ? NotFound() : Ok(policy);
    }

    /// <summary>Bind an issued quote into an active policy.</summary>
    [HttpPost]
    [Authorize(Policy = "Broker")]
    public async Task<ActionResult<PolicyDto>> Bind([FromBody] BindPolicyRequest request, CancellationToken ct)
    {
        var policy = await policyService.BindAsync(request, ct);
        return CreatedAtAction(nameof(GetById), new { id = policy.Id }, policy);
    }

    [HttpDelete("{id:guid}")]
    [Authorize(Policy = "Broker")]
    public async Task<ActionResult<PolicyDto>> Cancel(
        Guid id, [FromBody] CancelPolicyRequest request, CancellationToken ct)
        => Ok(await policyService.CancelAsync(id, request.Reason, ct));
}
