namespace TeamChat.Core.DTOs;

/// <summary>
/// Login request DTO.
/// </summary>
public record LoginDto(string Username, string Password);

/// <summary>
/// Registration request DTO.
/// </summary>
public record RegisterDto(string Username, string Email, string Password, string? DisplayName = null);

/// <summary>
/// Authentication response with JWT token.
/// </summary>
public record AuthResponseDto(
    string Token,
    string UserId,
    string Username,
    string? DisplayName,
    DateTime ExpiresAt);

/// <summary>
/// Token refresh request.
/// </summary>
public record RefreshTokenDto(string Token);
