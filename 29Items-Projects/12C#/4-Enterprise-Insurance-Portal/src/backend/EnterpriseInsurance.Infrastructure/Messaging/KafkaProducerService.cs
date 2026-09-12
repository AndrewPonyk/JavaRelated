using Confluent.Kafka;
using EnterpriseInsurance.Core.Interfaces;
using Microsoft.Extensions.Configuration;

namespace EnterpriseInsurance.Infrastructure.Messaging
{
    public class KafkaProducerService : IKafkaProducer
    {
        private readonly IProducer<Null, string> _producer;

        public KafkaProducerService(IConfiguration configuration)
        {
            var config = new ProducerConfig
            {
                BootstrapServers = configuration["Kafka:BootstrapServers"] ?? "localhost:9092"
            };
            _producer = new ProducerBuilder<Null, string>(config).Build();
        }

        public async Task PublishAsync(string topic, string message)
        {
            await _producer.ProduceAsync(topic, new Message<Null, string> { Value = message });
        }
    }
}
