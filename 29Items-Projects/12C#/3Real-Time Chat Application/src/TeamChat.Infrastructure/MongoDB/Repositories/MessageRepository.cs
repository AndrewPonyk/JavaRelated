using MongoDB.Bson;
using MongoDB.Driver;
using TeamChat.Core.Entities;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.Infrastructure.MongoDB.Repositories;

public class MessageRepository : IMessageRepository
{
    private readonly MongoDbContext _context;

    public MessageRepository(MongoDbContext context)
    {
        _context = context;
    }

    public async Task<Message?> GetByIdAsync(string id, CancellationToken cancellationToken = default)
    {
        return await _context.Messages
            .Find(m => m.Id == id && !m.IsDeleted)
            .FirstOrDefaultAsync(cancellationToken);
    }

    public async Task<IEnumerable<Message>> GetByChannelIdAsync(
        string channelId,
        int skip = 0,
        int take = 50,
        CancellationToken cancellationToken = default)
    {
        return await _context.Messages
            .Find(m => m.ChannelId == channelId && m.ThreadId == null && !m.IsDeleted)
            .SortByDescending(m => m.CreatedAt)
            .Skip(skip)
            .Limit(take)
            .ToListAsync(cancellationToken);
    }

    public async Task<IEnumerable<Message>> GetByThreadIdAsync(
        string threadId,
        int skip = 0,
        int take = 50,
        CancellationToken cancellationToken = default)
    {
        return await _context.Messages
            .Find(m => m.ThreadId == threadId && !m.IsDeleted)
            .SortBy(m => m.CreatedAt)
            .Skip(skip)
            .Limit(take)
            .ToListAsync(cancellationToken);
    }

    public async Task<Message> CreateAsync(Message message, CancellationToken cancellationToken = default)
    {
        message.Id = ObjectId.GenerateNewId().ToString();
        message.CreatedAt = DateTime.UtcNow;
        await _context.Messages.InsertOneAsync(message, cancellationToken: cancellationToken);
        return message;
    }

    public async Task UpdateAsync(Message message, CancellationToken cancellationToken = default)
    {
        message.IsEdited = true;
        message.EditedAt = DateTime.UtcNow;
        await _context.Messages.ReplaceOneAsync(
            m => m.Id == message.Id,
            message,
            cancellationToken: cancellationToken);
    }

    public async Task DeleteAsync(string id, CancellationToken cancellationToken = default)
    {
        // Soft delete
        var update = Builders<Message>.Update
            .Set(m => m.IsDeleted, true)
            .Set(m => m.Content, "[deleted]");
        await _context.Messages.UpdateOneAsync(
            m => m.Id == id,
            update,
            cancellationToken: cancellationToken);
    }

    public async Task AddReactionAsync(
        string messageId,
        string userId,
        ReactionType reactionType,
        CancellationToken cancellationToken = default)
    {
        var message = await GetByIdAsync(messageId, cancellationToken);
        if (message == null) return;

        var reaction = message.Reactions.FirstOrDefault(r => r.Type == reactionType);
        if (reaction == null)
        {
            reaction = new Reaction { Type = reactionType, UserIds = new List<string> { userId } };
            message.Reactions.Add(reaction);
        }
        else if (!reaction.UserIds.Contains(userId))
        {
            reaction.UserIds.Add(userId);
        }

        await _context.Messages.ReplaceOneAsync(
            m => m.Id == messageId,
            message,
            cancellationToken: cancellationToken);
    }

    public async Task RemoveReactionAsync(
        string messageId,
        string userId,
        ReactionType reactionType,
        CancellationToken cancellationToken = default)
    {
        var message = await GetByIdAsync(messageId, cancellationToken);
        if (message == null) return;

        var reaction = message.Reactions.FirstOrDefault(r => r.Type == reactionType);
        if (reaction != null)
        {
            reaction.UserIds.Remove(userId);
            if (reaction.UserIds.Count == 0)
            {
                message.Reactions.Remove(reaction);
            }
        }

        await _context.Messages.ReplaceOneAsync(
            m => m.Id == messageId,
            message,
            cancellationToken: cancellationToken);
    }

    public async Task<int> GetMessageCountByChannelAsync(string channelId, CancellationToken cancellationToken = default)
    {
        return (int)await _context.Messages
            .CountDocumentsAsync(m => m.ChannelId == channelId && !m.IsDeleted, cancellationToken: cancellationToken);
    }
}
