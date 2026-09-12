using Microsoft.AspNetCore.SignalR;

namespace Portal.Web.Hubs;

/// <summary>
/// Pushes real-time policy updates to connected brokers.
/// <see cref="Eventing.PolicyEventsKafkaConsumer"/> receives policy-events from Kafka
/// and broadcasts through this hub's IHubContext.
/// </summary>
public class PolicyHub : Hub
{
    public const string PolicyUpdatedMethod = "PolicyUpdated";
}
