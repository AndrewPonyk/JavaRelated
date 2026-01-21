using TeamChat.Core.Entities;

namespace TeamChat.API.Services;

/// <summary>
/// Service for generating and validating JWT tokens.
/// </summary>
public interface IJwtService
{
    /// <summary>
    /// Generates a JWT token for the specified user.
    /// </summary>
    string GenerateToken(User user);

    /// <summary>
    /// Validates a JWT token and returns the user ID if valid.
    /// </summary>
    string? ValidateToken(string token);

    /// <summary>
    /// Gets the token expiry time.
    /// </summary>
    DateTime GetExpiryTime();
}
