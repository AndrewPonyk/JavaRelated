using TeamChat.Core.Enums;

namespace TeamChat.Core.DTOs;

public record MessageDto(
    string Id,
    string ChannelId,
    string? ThreadId,
    string SenderId,
    string SenderName,
    string Content,
    MessageType Type,
    Sentiment? Sentiment,
    float? SentimentScore,
    FileAttachmentDto? Attachment,
    List<ReactionDto> Reactions,
    bool IsEdited,
    DateTime CreatedAt,
    DateTime? EditedAt
);

public record SendMessageDto(
    string ChannelId,
    string? ThreadId,
    string Content,
    string? AttachmentId
);

public record UpdateMessageDto(
    string Content
);

public record ReactionDto(
    ReactionType Type,
    int Count,
    bool UserReacted
);

public record AddReactionDto(
    string MessageId,
    ReactionType Type
);
