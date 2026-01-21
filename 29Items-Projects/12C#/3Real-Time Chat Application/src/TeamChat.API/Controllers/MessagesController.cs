using System.Security.Claims;
using Microsoft.AspNetCore.Mvc;
using TeamChat.Core.DTOs;
using TeamChat.Core.Entities;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class MessagesController : ControllerBase
{
    private readonly IMessageRepository _messageRepository;
    private readonly ILogger<MessagesController> _logger;

    public MessagesController(
        IMessageRepository messageRepository,
        ILogger<MessagesController> logger)
    {
        _messageRepository = messageRepository;
        _logger = logger;
    }

    private string? GetCurrentUserId()
    {
        return User?.FindFirst(ClaimTypes.NameIdentifier)?.Value
            ?? Request.Query["userId"].ToString();
    }

    private MessageDto MapToDto(Message m, string? currentUserId)
    {
        return new MessageDto(
            Id: m.Id,
            ChannelId: m.ChannelId,
            ThreadId: m.ThreadId,
            SenderId: m.SenderId,
            SenderName: m.SenderName,
            Content: m.Content,
            Type: m.Type,
            Sentiment: m.Sentiment,
            SentimentScore: m.SentimentScore,
            Attachment: m.Attachment != null ? new FileAttachmentDto(
                Id: m.Attachment.Id,
                FileName: m.Attachment.FileName,
                ContentType: m.Attachment.ContentType,
                SizeBytes: m.Attachment.SizeBytes,
                Url: m.Attachment.Url,
                ThumbnailUrl: m.Attachment.ThumbnailUrl
            ) : null,
            Reactions: m.Reactions.Select(r => new ReactionDto(
                Type: r.Type,
                Count: r.Count,
                UserReacted: !string.IsNullOrEmpty(currentUserId) && r.UserIds.Contains(currentUserId)
            )).ToList(),
            IsEdited: m.IsEdited,
            CreatedAt: m.CreatedAt,
            EditedAt: m.EditedAt
        );
    }

    /// <summary>
    /// Get messages for a channel with pagination.
    /// </summary>
    [HttpGet("channel/{channelId}")]
    public async Task<ActionResult<IEnumerable<MessageDto>>> GetChannelMessages(
        string channelId,
        [FromQuery] int skip = 0,
        [FromQuery] int take = 50)
    {
        // Validate pagination parameters
        if (skip < 0) skip = 0;
        if (take < 1) take = 1;
        if (take > 100) take = 100;

        var currentUserId = GetCurrentUserId();
        var messages = await _messageRepository.GetByChannelIdAsync(channelId, skip, take);
        var dtos = messages.Select(m => MapToDto(m, currentUserId));

        return Ok(dtos);
    }

    /// <summary>
    /// Get messages for a thread with pagination.
    /// </summary>
    [HttpGet("thread/{threadId}")]
    public async Task<ActionResult<IEnumerable<MessageDto>>> GetThreadMessages(
        string threadId,
        [FromQuery] int skip = 0,
        [FromQuery] int take = 50)
    {
        // Validate pagination parameters
        if (skip < 0) skip = 0;
        if (take < 1) take = 1;
        if (take > 100) take = 100;

        var currentUserId = GetCurrentUserId();
        var messages = await _messageRepository.GetByThreadIdAsync(threadId, skip, take);
        var dtos = messages.Select(m => MapToDto(m, currentUserId));

        return Ok(dtos);
    }

    /// <summary>
    /// Get a specific message by ID.
    /// </summary>
    [HttpGet("{id}")]
    public async Task<ActionResult<MessageDto>> GetMessage(string id)
    {
        if (string.IsNullOrWhiteSpace(id))
        {
            return BadRequest("Message ID is required");
        }

        var message = await _messageRepository.GetByIdAsync(id);

        if (message == null)
        {
            return NotFound();
        }

        var currentUserId = GetCurrentUserId();
        return Ok(MapToDto(message, currentUserId));
    }

    /// <summary>
    /// Get message count for a channel.
    /// </summary>
    [HttpGet("channel/{channelId}/count")]
    public async Task<ActionResult<int>> GetMessageCount(string channelId)
    {
        var count = await _messageRepository.GetMessageCountByChannelAsync(channelId);
        return Ok(count);
    }
}
