namespace EnterpriseInsurance.Core.Entities
{
    public class Policy
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public string PolicyNumber { get; set; } = string.Empty;
        public decimal PremiumAmount { get; set; }
        public string Status { get; set; } = "Draft"; // Draft, Bound, Expired
        public Guid CustomerId { get; set; }
        public Customer Customer { get; set; } = null!;
    }
}
