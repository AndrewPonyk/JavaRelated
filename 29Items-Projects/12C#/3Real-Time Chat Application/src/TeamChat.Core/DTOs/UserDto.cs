using TeamChat.Core.Enums;

namespace TeamChat.Core.DTOs;

public record UserDto(
    string Id,
    string Username,
    string DisplayName,
    string? AvatarUrl,
    UserStatus Status,
    DateTime? LastSeenAt
);

public record CreateUserDto(
    string Username,
    string Email,
    string Password,
    string DisplayName
);

public record UpdateUserDto(
    string? DisplayName,
    string? AvatarUrl
);

public record UserPresenceDto(
    string UserId,
    UserStatus Status,
    DateTime? LastSeenAt
);
