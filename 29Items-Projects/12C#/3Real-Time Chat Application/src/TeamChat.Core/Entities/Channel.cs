using TeamChat.Core.Enums;

namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a chat channel (room).
/// </summary>
public class Channel
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string? Description { get; set; }
    public ChannelType Type { get; set; } = ChannelType.Public;
    public string CreatedById { get; set; } = string.Empty;

    /// <summary>
    /// List of user IDs who are members of this channel.
    /// </summary>
    public List<string> MemberIds { get; set; } = new();

    /// <summary>
    /// Channel icon or image URL.
    /// </summary>
    public string? IconUrl { get; set; }

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAt { get; set; }

    /// <summary>
    /// Last message timestamp for sorting channels.
    /// </summary>
    public DateTime? LastMessageAt { get; set; }
}
