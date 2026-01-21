using MongoDB.Bson;
using MongoDB.Driver;
using TeamChat.Core.Entities;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.MongoDB.Repositories;

public class ChannelRepository : IChannelRepository
{
    private readonly MongoDbContext _context;

    public ChannelRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<Channel?> GetByIdAsync(string id, CancellationToken cancellationToken = default)
    {
        return await _context.Channels
            .Find(c => c.Id == id)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<IEnumerable<Channel>> GetByUserIdAsync(string userId, CancellationToken cancellationToken = default)
    {
        var filter = Builders<Channel>.Filter.AnyEq(c => c.MemberIds, userId);
        return await _context.Channels
            .Find(filter)
            .SortByDescending(c => c.LastMessageAt)
            .ToListAsync(cancellationToken);
    }

    public async Task<IEnumerable<Channel>> GetPublicChannelsAsync(CancellationToken cancellationToken = default)
    {
        return await _context.Channels
            .Find(c => c.Type == Core.Enums.ChannelType.Public)
            .SortByDescending(c => c.LastMessageAt)
            .ToListAsync(cancellationToken);
    }

    public async Task<Channel> CreateAsync(Channel channel, CancellationToken cancellationToken = default)
    {
        channel.Id = ObjectId.GenerateNewId().ToString();
        channel.CreatedAt = DateTime.UtcNow;
        await _context.Channels.InsertOneAsync(channel, cancellationToken: cancellationToken);
        return channel;
    }

    public async Task UpdateAsync(Channel channel, CancellationToken cancellationToken = default)
    {
        channel.UpdatedAt = DateTime.UtcNow;
        await _context.Channels.ReplaceOneAsync(
            c => c.Id == channel.Id,
            channel,
            cancellationToken: cancellationToken);
    }

    public async Task DeleteAsync(string id, CancellationToken cancellationToken = default)
    {
        await _context.Channels.DeleteOneAsync(c => c.Id == id, cancellationToken);
    }

    public async Task AddMemberAsync(string channelId, string userId, CancellationToken cancellationToken = default)
    {
        var update = Builders<Channel>.Update.AddToSet(c => c.MemberIds, userId);
        await _context.Channels.UpdateOneAsync(
            c => c.Id == channelId,
            update,
            cancellationToken: cancellationToken);
    }

    public async Task RemoveMemberAsync(string channelId, string userId, CancellationToken cancellationToken = default)
    {
        var update = Builders<Channel>.Update.Pull(c => c.MemberIds, userId);
        await _context.Channels.UpdateOneAsync(
            c => c.Id == channelId,
            update,
            cancellationToken: cancellationToken);
    }

    public async Task UpdateLastMessageAtAsync(string channelId, DateTime timestamp, CancellationToken cancellationToken = default)
    {
        var update = Builders<Channel>.Update.Set(c => c.LastMessageAt, timestamp);
        await _context.Channels.UpdateOneAsync(
            c => c.Id == channelId,
            update,
            cancellationToken: cancellationToken);
    }
}
