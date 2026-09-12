using EnterpriseInsurance.Core.Entities;
using EnterpriseInsurance.Core.Interfaces;

namespace EnterpriseInsurance.Core.Services
{
    public class PolicyService : IPolicyService
    {
        private readonly IPolicyRepository _policyRepository;
        private readonly ICustomerRepository _customerRepository;
        private readonly IKafkaProducer _kafkaProducer;

        public PolicyService(IPolicyRepository policyRepository, ICustomerRepository customerRepository, IKafkaProducer kafkaProducer)
        {
            _policyRepository = policyRepository;
            _customerRepository = customerRepository;
            _kafkaProducer = kafkaProducer;
        }

        public async Task<Policy> CreatePolicyAsync(Guid customerId, string planType, decimal initialPremium)
        {
            var customer = await _customerRepository.GetByIdAsync(customerId);
            if (customer == null) throw new ArgumentException("Customer not found");

            var policy = new Policy
            {
                CustomerId = customerId,
                PolicyNumber = $"POL-{DateTime.UtcNow:yyyyMMddHHmmss}-{new Random().Next(100, 999)}",
                PremiumAmount = initialPremium,
                Status = "Draft"
            };

            var created = await _policyRepository.AddAsync(policy);
            return created;
        }

        public async Task<PagedResult<Policy>> GetAllPoliciesAsync(int page = 1, int pageSize = 10)
        {
            return await _policyRepository.GetAllAsync(page, pageSize);
        }

        public async Task BindPolicyAsync(Guid policyId)
        {
            var policy = await _policyRepository.GetByIdAsync(policyId);
            if (policy == null) throw new ArgumentException("Policy not found");

            policy.Status = "Bound";
            await _policyRepository.UpdateAsync(policy);

            await _kafkaProducer.PublishAsync("dev_insurance_policies", $"Policy {policy.PolicyNumber} Bound");
        }

        public async Task DeletePolicyAsync(Guid policyId)
        {
            var policy = await _policyRepository.GetByIdAsync(policyId);
            if (policy == null) throw new ArgumentException("Policy not found");
            
            await _policyRepository.DeleteAsync(policyId);
        }
    }
}
