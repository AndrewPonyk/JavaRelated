using MongoDB.Bson;
using MongoDB.Driver;
using TeamChat.Core.Entities;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.MongoDB.Repositories;

public class UserRepository : IUserRepository
{
    private readonly MongoDbContext _context;

    public UserRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<User?> GetByIdAsync(string id, CancellationToken cancellationToken = default)
    {
        return await _context.Users
            .Find(u => u.Id == id)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<User?> GetByUsernameAsync(string username, CancellationToken cancellationToken = default)
    {
        return await _context.Users
            .Find(u => u.Username == username)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<User?> GetByEmailAsync(string email, CancellationToken cancellationToken = default)
    {
        return await _context.Users
            .Find(u => u.Email == email)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<IEnumerable<User>> GetByIdsAsync(IEnumerable<string> ids, CancellationToken cancellationToken = default)
    {
        var filter = Builders<User>.Filter.In(u => u.Id, ids);
        return await _context.Users
            .Find(filter)
            .ToListAsync(cancellationToken);
    }

    public async Task<User> CreateAsync(User user, CancellationToken cancellationToken = default)
    {
        user.Id = ObjectId.GenerateNewId().ToString();
        user.CreatedAt = DateTime.UtcNow;
        await _context.Users.InsertOneAsync(user, cancellationToken: cancellationToken);
        return user;
    }

    public async Task UpdateAsync(User user, CancellationToken cancellationToken = default)
    {
        user.UpdatedAt = DateTime.UtcNow;
        await _context.Users.ReplaceOneAsync(
            u => u.Id == user.Id,
            user,
            cancellationToken: cancellationToken);
    }

    public async Task DeleteAsync(string id, CancellationToken cancellationToken = default)
    {
        await _context.Users.DeleteOneAsync(u => u.Id == id, cancellationToken);
    }

    public async Task AddToChannelAsync(string userId, string channelId, CancellationToken cancellationToken = default)
    {
        var update = Builders<User>.Update.AddToSet(u => u.ChannelIds, channelId);
        await _context.Users.UpdateOneAsync(
            u => u.Id == userId,
            update,
            cancellationToken: cancellationToken);
    }

    public async Task RemoveFromChannelAsync(string userId, string channelId, CancellationToken cancellationToken = default)
    {
        var update = Builders<User>.Update.Pull(u => u.ChannelIds, channelId);
        await _context.Users.UpdateOneAsync(
            u => u.Id == userId,
            update,
            cancellationToken: cancellationToken);
    }
}
