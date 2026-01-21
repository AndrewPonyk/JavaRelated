using TeamChat.Core.Enums;

namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a chat message.
/// </summary>
public class Message
{
    public string Id { get; set; } = string.Empty;
    public string ChannelId { get; set; } = string.Empty;
    public string? ThreadId { get; set; }
    public string SenderId { get; set; } = string.Empty;
    public string SenderName { get; set; } = string.Empty;
    public string Content { get; set; } = string.Empty;
    public MessageType Type { get; set; } = MessageType.Text;

    /// <summary>
    /// Sentiment analysis result for the message.
    /// </summary>
    public Sentiment? Sentiment { get; set; }
    public float? SentimentScore { get; set; }

    /// <summary>
    /// File attachment if message contains a file.
    /// </summary>
    public FileAttachment? Attachment { get; set; }

    /// <summary>
    /// Reactions on this message.
    /// </summary>
    public List<Reaction> Reactions { get; set; } = new();

    public bool IsEdited { get; set; }
    public bool IsDeleted { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? EditedAt { get; set; }
}
