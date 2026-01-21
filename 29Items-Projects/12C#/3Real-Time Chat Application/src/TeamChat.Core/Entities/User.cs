using TeamChat.Core.Enums;

namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a user in the chat application.
/// </summary>
public class User
{
    public string Id { get; set; } = string.Empty;
    public string Username { get; set; } = string.Empty;
    public string Email { get; set; } = string.Empty;
    public string PasswordHash { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string? AvatarUrl { get; set; }
    public UserStatus Status { get; set; } = UserStatus.Offline;
    public DateTime? LastSeenAt { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime? UpdatedAt { get; set; }

    /// <summary>
    /// List of channel IDs the user is a member of.
    /// </summary>
    public List<string> ChannelIds { get; set; } = new();
}
