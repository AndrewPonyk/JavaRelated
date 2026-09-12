using EnterpriseInsurance.Api.Models;
using EnterpriseInsurance.Core.Entities;
using EnterpriseInsurance.Core.Interfaces;
using Microsoft.AspNetCore.Mvc;

namespace EnterpriseInsurance.Api.Controllers
{
    [ApiController]
    [Route("api/[controller]")]
    public class CustomersController : ControllerBase
    {
        private readonly ICustomerRepository _customerRepository;

        public CustomersController(ICustomerRepository customerRepository)
        {
            _customerRepository = customerRepository;
        }

        [HttpGet]
        public async Task<IActionResult> GetCustomers([FromQuery] int page = 1, [FromQuery] int pageSize = 10)
        {
            var pagedResult = await _customerRepository.GetAllAsync(page, pageSize);
            return Ok(pagedResult);
        }

        [HttpPost]
        public async Task<IActionResult> CreateCustomer([FromBody] CreateCustomerRequest request)
        {
            if (!ModelState.IsValid) return BadRequest(ModelState);

            var customer = new Customer
            {
                Name = request.Name,
                Email = request.Email
            };

            var created = await _customerRepository.AddAsync(customer);
            return CreatedAtAction(nameof(GetCustomers), new { id = created.Id }, created);
        }
    }
}
