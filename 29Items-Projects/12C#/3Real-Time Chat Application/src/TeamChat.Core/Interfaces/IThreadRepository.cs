using TeamChat.Core.Entities;

namespace TeamChat.Core.Interfaces;

public interface IThreadRepository
{
    Task<MessageThread?> GetByIdAsync(string id, CancellationToken cancellationToken = default);
    Task<MessageThread?> GetByParentMessageIdAsync(string parentMessageId, CancellationToken cancellationToken = default);
    Task<IEnumerable<MessageThread>> GetByChannelIdAsync(string channelId, CancellationToken cancellationToken = default);
    Task<MessageThread> CreateAsync(MessageThread thread, CancellationToken cancellationToken = default);
    Task UpdateAsync(MessageThread thread, CancellationToken cancellationToken = default);
    Task IncrementReplyCountAsync(string threadId, CancellationToken cancellationToken = default);
    Task AddParticipantAsync(string threadId, string userId, CancellationToken cancellationToken = default);
}
