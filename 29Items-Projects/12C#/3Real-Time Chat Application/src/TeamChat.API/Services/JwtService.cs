using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;
using TeamChat.Core.Entities;

namespace TeamChat.API.Services;

/// <summary>
/// JWT token generation and validation service.
/// </summary>
public class JwtService : IJwtService
{
    private readonly JwtSettings _settings;
    private readonly SymmetricSecurityKey _key;

    public JwtService(IOptions<JwtSettings> settings)
    {
        _settings = settings.Value;
        _key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_settings.Secret));
    }

    /// <inheritdoc />
    public string GenerateToken(User user)
    {
        var claims = new List<Claim>
        {
            new(ClaimTypes.NameIdentifier, user.Id),
            new(ClaimTypes.Name, user.Username),
            new(ClaimTypes.Email, user.Email),
            new("display_name", user.DisplayName ?? user.Username)
        };

        var credentials = new SigningCredentials(_key, SecurityAlgorithms.HmacSha256);
        var expires = GetExpiryTime();

        var tokenDescriptor = new SecurityTokenDescriptor
        {
            Subject = new ClaimsIdentity(claims),
            Expires = expires,
            Issuer = _settings.Issuer,
            Audience = _settings.Audience,
            SigningCredentials = credentials
        };

        var tokenHandler = new JwtSecurityTokenHandler();
        var token = tokenHandler.CreateToken(tokenDescriptor);

        return tokenHandler.WriteToken(token);
    }

    /// <inheritdoc />
    public string? ValidateToken(string token)
    {
        if (string.IsNullOrEmpty(token))
            return null;

        var tokenHandler = new JwtSecurityTokenHandler();

        try
        {
            var validationParameters = new TokenValidationParameters
            {
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = _key,
                ValidateIssuer = true,
                ValidIssuer = _settings.Issuer,
                ValidateAudience = true,
                ValidAudience = _settings.Audience,
                ValidateLifetime = true,
                ClockSkew = TimeSpan.Zero
            };

            var principal = tokenHandler.ValidateToken(token, validationParameters, out _);
            var userId = principal.FindFirst(ClaimTypes.NameIdentifier)?.Value;

            return userId;
        }
        catch
        {
            return null;
        }
    }

    /// <inheritdoc />
    public DateTime GetExpiryTime()
    {
        return DateTime.UtcNow.AddMinutes(_settings.ExpiryMinutes);
    }
}
