using EnterpriseInsurance.Core.Entities;
using EnterpriseInsurance.Core.Interfaces;
using EnterpriseInsurance.Core.Services;
using FluentAssertions;
using Moq;
using Xunit;

namespace EnterpriseInsurance.Tests
{
    public class PolicyServiceTests
    {
        private readonly Mock<IPolicyRepository> _policyRepoMock;
        private readonly Mock<ICustomerRepository> _customerRepoMock;
        private readonly Mock<IKafkaProducer> _kafkaProducerMock;
        private readonly PolicyService _sut;

        public PolicyServiceTests()
        {
            _policyRepoMock = new Mock<IPolicyRepository>();
            _customerRepoMock = new Mock<ICustomerRepository>();
            _kafkaProducerMock = new Mock<IKafkaProducer>();

            _sut = new PolicyService(_policyRepoMock.Object, _customerRepoMock.Object, _kafkaProducerMock.Object);
        }

        [Fact]
        public async Task CreatePolicyAsync_WhenCustomerExists_ShouldReturnDraftPolicy()
        {
            // Arrange
            var customerId = Guid.NewGuid();
            _customerRepoMock.Setup(x => x.GetByIdAsync(customerId)).ReturnsAsync(new Customer { Id = customerId });
            _policyRepoMock.Setup(x => x.AddAsync(It.IsAny<Policy>())).ReturnsAsync((Policy p) => p);

            // Act
            var result = await _sut.CreatePolicyAsync(customerId, "Premium", 500m);

            // Assert
            result.Should().NotBeNull();
            result.Status.Should().Be("Draft");
            result.PremiumAmount.Should().Be(500m);
            _policyRepoMock.Verify(x => x.AddAsync(It.IsAny<Policy>()), Times.Once);
        }
        
        [Fact]
        public async Task BindPolicyAsync_WhenPolicyExists_ShouldUpdateStatusAndPublishEvent()
        {
            // Arrange
            var policyId = Guid.NewGuid();
            var policy = new Policy { Id = policyId, Status = "Draft", PolicyNumber = "POL-TEST" };
            _policyRepoMock.Setup(x => x.GetByIdAsync(policyId)).ReturnsAsync(policy);

            // Act
            await _sut.BindPolicyAsync(policyId);

            // Assert
            policy.Status.Should().Be("Bound");
            _policyRepoMock.Verify(x => x.UpdateAsync(policy), Times.Once);
            _kafkaProducerMock.Verify(x => x.PublishAsync("dev_insurance_policies", "Policy POL-TEST Bound"), Times.Once);
        }

        [Fact]
        public async Task DeletePolicyAsync_WhenPolicyExists_ShouldDeletePolicy()
        {
            // Arrange
            var policyId = Guid.NewGuid();
            var policy = new Policy { Id = policyId };
            _policyRepoMock.Setup(x => x.GetByIdAsync(policyId)).ReturnsAsync(policy);

            // Act
            await _sut.DeletePolicyAsync(policyId);

            // Assert
            _policyRepoMock.Verify(x => x.DeleteAsync(policyId), Times.Once);
        }
    }
}
