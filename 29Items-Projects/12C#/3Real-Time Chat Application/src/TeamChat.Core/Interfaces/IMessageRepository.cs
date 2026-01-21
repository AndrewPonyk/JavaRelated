using TeamChat.Core.Entities;
using TeamChat.Core.Enums;

namespace TeamChat.Core.Interfaces;

public interface IMessageRepository
{
    Task<Message?> GetByIdAsync(string id, CancellationToken cancellationToken = default);

    Task<IEnumerable<Message>> GetByChannelIdAsync(
        string channelId,
        int skip = 0,
        int take = 50,
        CancellationToken cancellationToken = default);

    Task<IEnumerable<Message>> GetByThreadIdAsync(
        string threadId,
        int skip = 0,
        int take = 50,
        CancellationToken cancellationToken = default);

    Task<Message> CreateAsync(Message message, CancellationToken cancellationToken = default);

    Task UpdateAsync(Message message, CancellationToken cancellationToken = default);

    Task DeleteAsync(string id, CancellationToken cancellationToken = default);

    Task AddReactionAsync(
        string messageId,
        string userId,
        ReactionType reactionType,
        CancellationToken cancellationToken = default);

    Task RemoveReactionAsync(
        string messageId,
        string userId,
        ReactionType reactionType,
        CancellationToken cancellationToken = default);

    Task<int> GetMessageCountByChannelAsync(string channelId, CancellationToken cancellationToken = default);
}
