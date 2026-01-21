using Microsoft.AspNetCore.Mvc;
using TeamChat.Core.DTOs;
using TeamChat.Core.Entities;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class UsersController : ControllerBase
{
    private readonly IUserRepository _userRepository;
    private readonly IPresenceService _presenceService;
    private readonly ILogger<UsersController> _logger;

    public UsersController(
        IUserRepository userRepository,
        IPresenceService presenceService,
        ILogger<UsersController> logger)
    {
        _userRepository = userRepository;
        _presenceService = presenceService;
        _logger = logger;
    }

    /// <summary>
    /// Get a user by ID.
    /// </summary>
    [HttpGet("{id}")]
    public async Task<ActionResult<UserDto>> GetUser(string id)
    {
        var user = await _userRepository.GetByIdAsync(id);

        if (user == null)
        {
            return NotFound();
        }

        // Get real-time status from Redis
        var status = await _presenceService.GetStatusAsync(id);

        var dto = new UserDto(
            Id: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName,
            AvatarUrl: user.AvatarUrl,
            Status: status,
            LastSeenAt: user.LastSeenAt
        );

        return Ok(dto);
    }

    /// <summary>
    /// Get a user by username.
    /// </summary>
    [HttpGet("username/{username}")]
    public async Task<ActionResult<UserDto>> GetUserByUsername(string username)
    {
        var user = await _userRepository.GetByUsernameAsync(username);

        if (user == null)
        {
            return NotFound();
        }

        var status = await _presenceService.GetStatusAsync(user.Id);

        var dto = new UserDto(
            Id: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName,
            AvatarUrl: user.AvatarUrl,
            Status: status,
            LastSeenAt: user.LastSeenAt
        );

        return Ok(dto);
    }

    /// <summary>
    /// Create a new user (registration).
    /// </summary>
    [HttpPost]
    public async Task<ActionResult<UserDto>> CreateUser([FromBody] CreateUserDto dto)
    {
        // Check if username exists
        var existing = await _userRepository.GetByUsernameAsync(dto.Username);
        if (existing != null)
        {
            return BadRequest("Username already exists");
        }

        // Check if email exists
        existing = await _userRepository.GetByEmailAsync(dto.Email);
        if (existing != null)
        {
            return BadRequest("Email already exists");
        }

        var user = new User
        {
            Username = dto.Username,
            Email = dto.Email,
            DisplayName = dto.DisplayName,
            PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.Password) // TODO: Use proper password hashing
        };

        user = await _userRepository.CreateAsync(user);

        _logger.LogInformation("User {UserId} ({Username}) created", user.Id, user.Username);

        var result = new UserDto(
            Id: user.Id,
            Username: user.Username,
            DisplayName: user.DisplayName,
            AvatarUrl: user.AvatarUrl,
            Status: user.Status,
            LastSeenAt: user.LastSeenAt
        );

        return CreatedAtAction(nameof(GetUser), new { id = user.Id }, result);
    }

    /// <summary>
    /// Update a user's profile.
    /// </summary>
    [HttpPut("{id}")]
    public async Task<ActionResult> UpdateUser(string id, [FromBody] UpdateUserDto dto)
    {
        var user = await _userRepository.GetByIdAsync(id);

        if (user == null)
        {
            return NotFound();
        }

        if (!string.IsNullOrEmpty(dto.DisplayName))
            user.DisplayName = dto.DisplayName;
        if (dto.AvatarUrl != null)
            user.AvatarUrl = dto.AvatarUrl;

        await _userRepository.UpdateAsync(user);

        return NoContent();
    }

    /// <summary>
    /// Get multiple users by IDs.
    /// </summary>
    [HttpPost("batch")]
    public async Task<ActionResult<IEnumerable<UserDto>>> GetUsers([FromBody] IEnumerable<string> ids)
    {
        var users = await _userRepository.GetByIdsAsync(ids);
        var onlineUsers = await _presenceService.GetOnlineUsersAsync(ids);
        var onlineSet = onlineUsers.ToHashSet();

        var dtos = users.Select(u => new UserDto(
            Id: u.Id,
            Username: u.Username,
            DisplayName: u.DisplayName,
            AvatarUrl: u.AvatarUrl,
            Status: onlineSet.Contains(u.Id) ? Core.Enums.UserStatus.Online : Core.Enums.UserStatus.Offline,
            LastSeenAt: u.LastSeenAt
        ));

        return Ok(dtos);
    }
}
