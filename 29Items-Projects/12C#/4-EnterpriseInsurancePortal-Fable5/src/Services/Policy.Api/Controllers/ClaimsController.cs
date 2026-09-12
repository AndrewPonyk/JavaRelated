using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Policy.Api.Models;
using Policy.Api.Services;

namespace Policy.Api.Controllers;

[ApiController]
[Authorize]
public class ClaimsController(IClaimService claimService) : ControllerBase
{
    [HttpGet("api/v1/policies/{policyId:guid}/claims")]
    public async Task<ActionResult<IReadOnlyList<ClaimDto>>> ListForPolicy(Guid policyId, CancellationToken ct)
        => Ok(await claimService.ListForPolicyAsync(policyId, ct));

    /// <summary>Customers can file claims against their own policies; brokers against any.</summary>
    [HttpPost("api/v1/policies/{policyId:guid}/claims")]
    public async Task<ActionResult<ClaimDto>> File(
        Guid policyId, [FromBody] FileClaimRequest request, CancellationToken ct)
    {
        var claim = await claimService.FileAsync(policyId, request, ct);
        return CreatedAtAction(nameof(ListForPolicy), new { policyId }, claim);
    }

    /// <summary>Adjudication (review/approve/reject/pay) is broker/underwriter work only.</summary>
    [HttpPost("api/v1/claims/{claimId:guid}/status")]
    [Authorize(Policy = "Broker")]
    public async Task<ActionResult<ClaimDto>> UpdateStatus(
        Guid claimId, [FromBody] UpdateClaimStatusRequest request, CancellationToken ct)
        => Ok(await claimService.UpdateStatusAsync(claimId, request, ct));
}
