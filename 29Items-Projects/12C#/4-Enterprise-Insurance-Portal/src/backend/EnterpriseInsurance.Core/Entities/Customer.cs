namespace EnterpriseInsurance.Core.Entities
{
    public class Customer
    {
        public Guid Id { get; set; } = Guid.NewGuid();
        public string Name { get; set; } = string.Empty;
        public string Email { get; set; } = string.Empty;
        public ICollection<Policy> Policies { get; set; } = new List<Policy>();
    }
}
