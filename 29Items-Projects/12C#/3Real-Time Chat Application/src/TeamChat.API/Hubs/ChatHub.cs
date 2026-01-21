using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;
using TeamChat.Core.DTOs;
using TeamChat.Core.Entities;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Hubs;

/// <summary>
/// SignalR hub for real-time chat messaging.
/// </summary>
[Authorize]
public class ChatHub : Hub
{
    private readonly IMessageRepository _messageRepository;
    private readonly IChannelRepository _channelRepository;
    private readonly IThreadRepository _threadRepository;
    private readonly IPresenceService _presenceService;
    private readonly ISentimentAnalyzer _sentimentAnalyzer;
    private readonly ILogger<ChatHub> _logger;

    public ChatHub(
        IMessageRepository messageRepository,
        IChannelRepository channelRepository,
        IThreadRepository threadRepository,
        IPresenceService presenceService,
        ISentimentAnalyzer sentimentAnalyzer,
        ILogger<ChatHub> logger)
    {
        _messageRepository = messageRepository;
        _channelRepository = channelRepository;
        _threadRepository = threadRepository;
        _presenceService = presenceService;
        _sentimentAnalyzer = sentimentAnalyzer;
        _logger = logger;
    }

    public override async Task OnConnectedAsync()
    {
        var userId = GetUserId();
        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetOnlineAsync(userId, Context.ConnectionId);
            _logger.LogInformation("User {UserId} connected with connection {ConnectionId}", userId, Context.ConnectionId);
        }

        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        var userId = GetUserId();
        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetOfflineAsync(userId, Context.ConnectionId);
            _logger.LogInformation("User {UserId} disconnected from connection {ConnectionId}", userId, Context.ConnectionId);
        }

        if (exception != null)
        {
            _logger.LogWarning(exception, "Client disconnected with error");
        }

        await base.OnDisconnectedAsync(exception);
    }

    /// <summary>
    /// Join a channel to receive messages.
    /// </summary>
    public async Task JoinChannel(string channelId)
    {
        await Groups.AddToGroupAsync(Context.ConnectionId, channelId);
        _logger.LogDebug("Connection {ConnectionId} joined channel {ChannelId}", Context.ConnectionId, channelId);
    }

    /// <summary>
    /// Leave a channel.
    /// </summary>
    public async Task LeaveChannel(string channelId)
    {
        await Groups.RemoveFromGroupAsync(Context.ConnectionId, channelId);
        _logger.LogDebug("Connection {ConnectionId} left channel {ChannelId}", Context.ConnectionId, channelId);
    }

    /// <summary>
    /// Send a message to a channel.
    /// </summary>
    public async Task SendMessage(string channelId, string content, string? threadId = null)
    {
        var userId = GetUserId();
        var userName = GetUserName();

        if (string.IsNullOrEmpty(userId))
        {
            throw new HubException("User not authenticated");
        }

        // Analyze sentiment
        var sentimentResult = await _sentimentAnalyzer.AnalyzeAsync(content);

        var message = new Message
        {
            ChannelId = channelId,
            ThreadId = threadId,
            SenderId = userId,
            SenderName = userName,
            Content = content,
            Type = MessageType.Text,
            Sentiment = sentimentResult.Sentiment,
            SentimentScore = sentimentResult.Score
        };

        // Save to database
        message = await _messageRepository.CreateAsync(message);

        // Update channel's last message timestamp
        await _channelRepository.UpdateLastMessageAtAsync(channelId, message.CreatedAt);

        // If this is a thread reply, update thread
        if (!string.IsNullOrEmpty(threadId))
        {
            await _threadRepository.IncrementReplyCountAsync(threadId);
            await _threadRepository.AddParticipantAsync(threadId, userId);
        }

        // Create DTO for client
        var messageDto = new MessageDto(
            Id: message.Id,
            ChannelId: message.ChannelId,
            ThreadId: message.ThreadId,
            SenderId: message.SenderId,
            SenderName: message.SenderName,
            Content: message.Content,
            Type: message.Type,
            Sentiment: message.Sentiment,
            SentimentScore: message.SentimentScore,
            Attachment: null,
            Reactions: new List<ReactionDto>(),
            IsEdited: false,
            CreatedAt: message.CreatedAt,
            EditedAt: null
        );

        // Broadcast to channel
        await Clients.Group(channelId).SendAsync("ReceiveMessage", messageDto);

        _logger.LogInformation("Message sent to channel {ChannelId} by user {UserId}", channelId, userId);
    }

    /// <summary>
    /// Edit an existing message.
    /// </summary>
    public async Task EditMessage(string messageId, string newContent)
    {
        var userId = GetUserId();
        var message = await _messageRepository.GetByIdAsync(messageId);

        if (message == null)
        {
            throw new HubException("Message not found");
        }

        if (message.SenderId != userId)
        {
            throw new HubException("You can only edit your own messages");
        }

        message.Content = newContent;
        await _messageRepository.UpdateAsync(message);

        await Clients.Group(message.ChannelId).SendAsync("MessageEdited", messageId, newContent, DateTime.UtcNow);
    }

    /// <summary>
    /// Delete a message.
    /// </summary>
    public async Task DeleteMessage(string messageId)
    {
        var userId = GetUserId();
        var message = await _messageRepository.GetByIdAsync(messageId);

        if (message == null)
        {
            throw new HubException("Message not found");
        }

        if (message.SenderId != userId)
        {
            throw new HubException("You can only delete your own messages");
        }

        await _messageRepository.DeleteAsync(messageId);

        await Clients.Group(message.ChannelId).SendAsync("MessageDeleted", messageId);
    }

    /// <summary>
    /// Add a reaction to a message.
    /// </summary>
    public async Task AddReaction(string messageId, ReactionType reactionType)
    {
        var userId = GetUserId();
        var message = await _messageRepository.GetByIdAsync(messageId);

        if (message == null)
        {
            throw new HubException("Message not found");
        }

        await _messageRepository.AddReactionAsync(messageId, userId!, reactionType);

        await Clients.Group(message.ChannelId).SendAsync("ReactionAdded", messageId, reactionType, userId);
    }

    /// <summary>
    /// Remove a reaction from a message.
    /// </summary>
    public async Task RemoveReaction(string messageId, ReactionType reactionType)
    {
        var userId = GetUserId();
        var message = await _messageRepository.GetByIdAsync(messageId);

        if (message == null)
        {
            throw new HubException("Message not found");
        }

        await _messageRepository.RemoveReactionAsync(messageId, userId!, reactionType);

        await Clients.Group(message.ChannelId).SendAsync("ReactionRemoved", messageId, reactionType, userId);
    }

    /// <summary>
    /// Signal that user is typing in a channel.
    /// </summary>
    public async Task StartTyping(string channelId)
    {
        var userId = GetUserId();
        var userName = GetUserName();

        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetTypingAsync(userId, channelId, true);
            await Clients.OthersInGroup(channelId).SendAsync("UserTyping", userId, userName, true);
        }
    }

    /// <summary>
    /// Signal that user stopped typing in a channel.
    /// </summary>
    public async Task StopTyping(string channelId)
    {
        var userId = GetUserId();
        var userName = GetUserName();

        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetTypingAsync(userId, channelId, false);
            await Clients.OthersInGroup(channelId).SendAsync("UserTyping", userId, userName, false);
        }
    }

    private string? GetUserId()
    {
        // First try to get from JWT claims (authenticated user)
        var userId = Context.User?.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        if (!string.IsNullOrEmpty(userId))
            return userId;

        // Fallback to query string for development/testing
        return Context.GetHttpContext()?.Request.Query["userId"].ToString();
    }

    private string GetUserName()
    {
        // First try to get from JWT claims (authenticated user)
        var userName = Context.User?.FindFirst(ClaimTypes.Name)?.Value;
        if (!string.IsNullOrEmpty(userName))
            return userName;

        // Try display name claim
        var displayName = Context.User?.FindFirst("display_name")?.Value;
        if (!string.IsNullOrEmpty(displayName))
            return displayName;

        // Fallback to query string for development/testing
        return Context.GetHttpContext()?.Request.Query["userName"].ToString() ?? "Anonymous";
    }
}
