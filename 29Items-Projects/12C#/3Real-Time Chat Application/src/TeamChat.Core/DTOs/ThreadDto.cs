namespace TeamChat.Core.DTOs;

public record ThreadDto(
    string Id,
    string ChannelId,
    string ParentMessageId,
    MessageDto ParentMessage,
    int ReplyCount,
    List<UserDto> Participants,
    DateTime? LastReplyAt
);

public record CreateThreadDto(
    string ChannelId,
    string ParentMessageId,
    string FirstReplyContent
);
