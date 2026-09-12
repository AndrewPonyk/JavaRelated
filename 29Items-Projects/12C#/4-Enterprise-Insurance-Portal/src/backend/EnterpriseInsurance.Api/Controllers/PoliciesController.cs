using EnterpriseInsurance.Api.Models;
using EnterpriseInsurance.Core.Interfaces;
using Microsoft.AspNetCore.Mvc;

namespace EnterpriseInsurance.Api.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class PoliciesController : ControllerBase
    {
        private readonly IPolicyService _policyService;

        public PoliciesController(IPolicyService policyService)
        {
            _policyService = policyService;
        }

        [HttpGet]
        public async Task<IActionResult> GetPolicies([FromQuery] int page = 1, [FromQuery] int pageSize = 10)
        {
            var pagedResult = await _policyService.GetAllPoliciesAsync(page, pageSize);
            return Ok(pagedResult);
        }

        [HttpPost]
        public async Task<IActionResult> CreatePolicy([FromBody] CreatePolicyRequest request)
        {
            if (!ModelState.IsValid) return BadRequest(ModelState);

            try
            {
                var policy = await _policyService.CreatePolicyAsync(request.CustomerId, request.PlanType, request.InitialPremium);
                return CreatedAtAction(nameof(GetPolicies), new { id = policy.Id }, policy);
            }
            catch (ArgumentException ex)
            {
                return BadRequest(ex.Message);
            }
        }

        [HttpPost("{id}/bind")]
        public async Task<IActionResult> BindPolicy(Guid id)
        {
            try
            {
                await _policyService.BindPolicyAsync(id);
                return Ok(new { message = "Policy bound successfully" });
            }
            catch (ArgumentException ex)
            {
                return NotFound(ex.Message);
            }
        }

        [HttpDelete("{id}")]
        public async Task<IActionResult> DeletePolicy(Guid id)
        {
            try
            {
                await _policyService.DeletePolicyAsync(id);
                return NoContent();
            }
            catch (ArgumentException ex)
            {
                return NotFound(ex.Message);
            }
        }
    }
}
