namespace EnterpriseInsurance.Core.Interfaces
{
    public interface IKafkaProducer
    {
        Task PublishAsync(string topic, string message);
    }
}
