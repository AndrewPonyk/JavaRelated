using System.ComponentModel.DataAnnotations;

namespace EnterpriseInsurance.Api.Models
{
    public class CreateCustomerRequest
    {
        [Required]
        public string Name { get; set; } = string.Empty;
        
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;
    }
}
