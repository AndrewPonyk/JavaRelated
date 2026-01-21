using TeamChat.Core.Entities;

namespace TeamChat.Core.Interfaces;

public interface IChannelRepository
{
    Task<Channel?> GetByIdAsync(string id, CancellationToken cancellationToken = default);
    Task<IEnumerable<Channel>> GetByUserIdAsync(string userId, CancellationToken cancellationToken = default);
    Task<IEnumerable<Channel>> GetPublicChannelsAsync(CancellationToken cancellationToken = default);
    Task<Channel> CreateAsync(Channel channel, CancellationToken cancellationToken = default);
    Task UpdateAsync(Channel channel, CancellationToken cancellationToken = default);
    Task DeleteAsync(string id, CancellationToken cancellationToken = default);
    Task AddMemberAsync(string channelId, string userId, CancellationToken cancellationToken = default);
    Task RemoveMemberAsync(string channelId, string userId, CancellationToken cancellationToken = default);
    Task UpdateLastMessageAtAsync(string channelId, DateTime timestamp, CancellationToken cancellationToken = default);
}
