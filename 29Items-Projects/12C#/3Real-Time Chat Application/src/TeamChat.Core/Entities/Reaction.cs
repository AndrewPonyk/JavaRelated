using TeamChat.Core.Enums;

namespace TeamChat.Core.Entities;

/// <summary>
/// Represents a reaction on a message.
/// </summary>
public class Reaction
{
    /// <summary>
    /// The type of reaction (emoji).
    /// </summary>
    public ReactionType Type { get; set; }

    /// <summary>
    /// User IDs who added this reaction.
    /// </summary>
    public List<string> UserIds { get; set; } = new();

    /// <summary>
    /// Count of users who added this reaction.
    /// </summary>
    public int Count => UserIds.Count;
}
