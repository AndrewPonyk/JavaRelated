using TeamChat.Core.Entities;

namespace TeamChat.Core.Interfaces;

public interface IUserRepository
{
    Task<User?> GetByIdAsync(string id, CancellationToken cancellationToken = default);
    Task<User?> GetByUsernameAsync(string username, CancellationToken cancellationToken = default);
    Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default);
    Task<IEnumerable<User>> GetByIdsAsync(IEnumerable<string> ids, CancellationToken cancellationToken = default);
    Task<User> CreateAsync(User user, CancellationToken cancellationToken = default);
    Task UpdateAsync(User user, CancellationToken cancellationToken = default);
    Task DeleteAsync(string id, CancellationToken cancellationToken = default);
    Task AddToChannelAsync(string userId, string channelId, CancellationToken cancellationToken = default);
    Task RemoveFromChannelAsync(string userId, string channelId, CancellationToken cancellationToken = default);
}
