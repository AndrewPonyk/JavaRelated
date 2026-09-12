using System.ComponentModel.DataAnnotations;

namespace EnterpriseInsurance.Api.Models
{
    public class CreatePolicyRequest
    {
        [Required]
        public Guid CustomerId { get; set; }

        [Required]
        [Range(100, 10000)]
        public decimal InitialPremium { get; set; }

        [Required]
        [StringLength(50, MinimumLength = 3)]
        public string PlanType { get; set; } = string.Empty;
    }
}
