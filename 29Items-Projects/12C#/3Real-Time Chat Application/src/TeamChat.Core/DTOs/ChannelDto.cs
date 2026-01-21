using TeamChat.Core.Enums;

namespace TeamChat.Core.DTOs;

public record ChannelDto(
    string Id,
    string Name,
    string? Description,
    ChannelType Type,
    string? IconUrl,
    int MemberCount,
    DateTime? LastMessageAt
);

public record CreateChannelDto(
    string Name,
    string? Description,
    ChannelType Type
);

public record UpdateChannelDto(
    string? Name,
    string? Description,
    string? IconUrl
);

public record ChannelMemberDto(
    string UserId,
    string Username,
    string DisplayName,
    UserStatus Status
);
