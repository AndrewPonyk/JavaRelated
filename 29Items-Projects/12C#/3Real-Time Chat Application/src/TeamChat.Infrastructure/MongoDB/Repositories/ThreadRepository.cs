using MongoDB.Bson;
using MongoDB.Driver;
using TeamChat.Core.Entities;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.MongoDB.Repositories;

public class ThreadRepository : IThreadRepository
{
    private readonly MongoDbContext _context;

    public ThreadRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<MessageThread?> GetByIdAsync(string id, CancellationToken cancellationToken = default)
    {
        return await _context.Threads
            .Find(t => t.Id == id)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<MessageThread?> GetByParentMessageIdAsync(string parentMessageId, CancellationToken cancellationToken = default)
    {
        return await _context.Threads
            .Find(t => t.ParentMessageId == parentMessageId)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<IEnumerable<MessageThread>> GetByChannelIdAsync(string channelId, CancellationToken cancellationToken = default)
    {
        return await _context.Threads
            .Find(t => t.ChannelId == channelId)
            .SortByDescending(t => t.LastReplyAt)
            .ToListAsync(cancellationToken);
    }

    public async Task<MessageThread> CreateAsync(MessageThread thread, CancellationToken cancellationToken = default)
    {
        thread.Id = ObjectId.GenerateNewId().ToString();
        thread.CreatedAt = DateTime.UtcNow;
        await _context.Threads.InsertOneAsync(thread, cancellationToken: cancellationToken);
        return thread;
    }

    public async Task UpdateAsync(MessageThread thread, CancellationToken cancellationToken = default)
    {
        await _context.Threads.ReplaceOneAsync(
            t => t.Id == thread.Id,
            thread,
            cancellationToken: cancellationToken);
    }

    public async Task IncrementReplyCountAsync(string threadId, CancellationToken cancellationToken = default)
    {
        var update = Builders<MessageThread>.Update
            .Inc(t => t.ReplyCount, 1)
            .Set(t => t.LastReplyAt, DateTime.UtcNow);
        await _context.Threads.UpdateOneAsync(
            t => t.Id == threadId,
            update,
            cancellationToken: cancellationToken);
    }

    public async Task AddParticipantAsync(string threadId, string userId, CancellationToken cancellationToken = default)
    {
        var update = Builders<MessageThread>.Update.AddToSet(t => t.ParticipantIds, userId);
        await _context.Threads.UpdateOneAsync(
            t => t.Id == threadId,
            update,
            cancellationToken: cancellationToken);
    }
}
