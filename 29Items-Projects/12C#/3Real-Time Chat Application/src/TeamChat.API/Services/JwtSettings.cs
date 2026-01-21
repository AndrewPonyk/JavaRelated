namespace TeamChat.API.Services;

/// <summary>
/// JWT configuration settings.
/// </summary>
public class JwtSettings
{
    public string Secret { get; set; } = "TeamChatDefaultSecretKey_MustBeAtLeast32Chars!";
    public string Issuer { get; set; } = "TeamChat";
    public string Audience { get; set; } = "TeamChat";
    public int ExpiryMinutes { get; set; } = 60;
}
