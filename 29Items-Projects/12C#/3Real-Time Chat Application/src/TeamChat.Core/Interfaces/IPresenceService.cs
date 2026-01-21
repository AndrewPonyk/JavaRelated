using TeamChat.Core.Enums;

namespace TeamChat.Core.Interfaces;

public interface IPresenceService
{
    Task SetOnlineAsync(string userId, string connectionId, CancellationToken cancellationToken = default);
    Task SetOfflineAsync(string userId, string connectionId, CancellationToken cancellationToken = default);
    Task SetStatusAsync(string userId, UserStatus status, CancellationToken cancellationToken = default);
    Task<UserStatus> GetStatusAsync(string userId, CancellationToken cancellationToken = default);
    Task<IEnumerable<string>> GetOnlineUsersAsync(IEnumerable<string> userIds, CancellationToken cancellationToken = default);
    Task<IEnumerable<string>> GetConnectionsAsync(string userId, CancellationToken cancellationToken = default);
    Task SetTypingAsync(string userId, string channelId, bool isTyping, CancellationToken cancellationToken = default);
    Task<IEnumerable<string>> GetTypingUsersAsync(string channelId, CancellationToken cancellationToken = default);
}
