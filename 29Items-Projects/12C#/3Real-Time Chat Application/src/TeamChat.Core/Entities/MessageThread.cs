namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a message thread (replies to a parent message).
/// </summary>
public class MessageThread
{
    public string Id { get; set; } = string.Empty;

    /// <summary>
    /// The channel this thread belongs to.
    /// </summary>
    public string ChannelId { get; set; } = string.Empty;

    /// <summary>
    /// The parent message ID that started this thread.
    /// </summary>
    public string ParentMessageId { get; set; } = string.Empty;

    /// <summary>
    /// User IDs participating in this thread.
    /// </summary>
    public List<string> ParticipantIds { get; set; } = new();

    /// <summary>
    /// Number of replies in this thread.
    /// </summary>
    public int ReplyCount { get; set; }

    /// <summary>
    /// Last reply timestamp.
    /// </summary>
    public DateTime? LastReplyAt { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
