using System.Text.RegularExpressions;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using TeamChat.API.Services;
using TeamChat.Core.DTOs;
using TeamChat.Core.Entities;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Controllers;

/// <summary>
/// Authentication controller for user login and registration.
/// </summary>
[ApiController]
[Route("api/[controller]")]
public class AuthController : ControllerBase
{
    private readonly IUserRepository _userRepository;
    private readonly IJwtService _jwtService;
    private readonly ILogger<AuthController> _logger;

    public AuthController(
        IUserRepository userRepository,
        IJwtService jwtService,
        ILogger<AuthController> logger)
    {
        _userRepository = userRepository;
        _jwtService = jwtService;
        _logger = logger;
    }

    /// <summary>
    /// Authenticates a user and returns a JWT token.
    /// </summary>
    [HttpPost("login")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponseDto>> Login([FromBody] LoginDto dto)
    {
        if (string.IsNullOrWhiteSpace(dto.Username) || string.IsNullOrWhiteSpace(dto.Password))
        {
            return BadRequest(new { error = "Username and password are required" });
        }

        var user = await _userRepository.GetByUsernameAsync(dto.Username);
        if (user == null)
        {
            _logger.LogWarning("Login attempt for non-existent user: {Username}", dto.Username);
            return Unauthorized(new { error = "Invalid username or password" });
        }

        if (!BCrypt.Net.BCrypt.Verify(dto.Password, user.PasswordHash))
        {
            _logger.LogWarning("Invalid password for user: {Username}", dto.Username);
            return Unauthorized(new { error = "Invalid username or password" });
        }

        var token = _jwtService.GenerateToken(user);
        var expiresAt = _jwtService.GetExpiryTime();

        _logger.LogInformation("User logged in: {Username}", dto.Username);

        return Ok(new AuthResponseDto(
            Token: token,
            UserId: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName,
            ExpiresAt: expiresAt));
    }

    private static readonly Regex UsernameRegex = new(@"^[a-zA-Z0-9_]{3,30}$", RegexOptions.Compiled);
    private static readonly Regex EmailRegex = new(@"^[^@\s]+@[^@\s]+\.[^@\s]+$", RegexOptions.Compiled);

    /// <summary>
    /// Registers a new user account.
    /// </summary>
    [HttpPost("register")]
    [AllowAnonymous]
    public async Task<ActionResult<AuthResponseDto>> Register([FromBody] RegisterDto dto)
    {
        if (string.IsNullOrWhiteSpace(dto.Username) ||
            string.IsNullOrWhiteSpace(dto.Email) ||
            string.IsNullOrWhiteSpace(dto.Password))
        {
            return BadRequest(new { error = "Username, email, and password are required" });
        }

        // Validate username format
        if (!UsernameRegex.IsMatch(dto.Username))
        {
            return BadRequest(new { error = "Username must be 3-30 characters and contain only letters, numbers, and underscores" });
        }

        // Validate email format
        if (!EmailRegex.IsMatch(dto.Email))
        {
            return BadRequest(new { error = "Invalid email format" });
        }

        // Validate password strength
        if (dto.Password.Length < 8)
        {
            return BadRequest(new { error = "Password must be at least 8 characters" });
        }

        // Check for existing username
        var existingUser = await _userRepository.GetByUsernameAsync(dto.Username);
        if (existingUser != null)
        {
            return Conflict(new { error = "Username already exists" });
        }

        // Check for existing email
        existingUser = await _userRepository.GetByEmailAsync(dto.Email);
        if (existingUser != null)
        {
            return Conflict(new { error = "Email already registered" });
        }

        var user = new User
        {
            Username = dto.Username,
            Email = dto.Email,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.Password),
            DisplayName = dto.DisplayName ?? dto.Username,
            Status = UserStatus.Online,
            CreatedAt = DateTime.UtcNow
        };

        var createdUser = await _userRepository.CreateAsync(user);

        var token = _jwtService.GenerateToken(createdUser);
        var expiresAt = _jwtService.GetExpiryTime();

        _logger.LogInformation("New user registered: {Username}", dto.Username);

        return CreatedAtAction(
            nameof(GetCurrentUser),
            new AuthResponseDto(
                Token: token,
                UserId: createdUser.Id,
                Username: createdUser.Username,
                DisplayName: createdUser.DisplayName,
                ExpiresAt: expiresAt));
    }

    /// <summary>
    /// Gets the current authenticated user's profile.
    /// </summary>
    [HttpGet("me")]
    [Authorize]
    public async Task<ActionResult<UserDto>> GetCurrentUser()
    {
        var userId = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
        if (string.IsNullOrEmpty(userId))
        {
            return Unauthorized();
        }

        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null)
        {
            return NotFound();
        }

        return Ok(new UserDto(
            Id: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName ?? user.Username,
            AvatarUrl: user.AvatarUrl,
            Status: user.Status,
            LastSeenAt: user.LastSeenAt));
    }

    /// <summary>
    /// Refreshes the JWT token for the current user.
    /// </summary>
    [HttpPost("refresh")]
    [Authorize]
    public async Task<ActionResult<AuthResponseDto>> RefreshToken()
    {
        var userId = User.FindFirst(System.Security.Claims.ClaimTypes.NameIdentifier)?.Value;
        if (string.IsNullOrEmpty(userId))
        {
            return Unauthorized();
        }

        var user = await _userRepository.GetByIdAsync(userId);
        if (user == null)
        {
            return NotFound();
        }

        var token = _jwtService.GenerateToken(user);
        var expiresAt = _jwtService.GetExpiryTime();

        return Ok(new AuthResponseDto(
            Token: token,
            UserId: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName,
            ExpiresAt: expiresAt));
    }
}
