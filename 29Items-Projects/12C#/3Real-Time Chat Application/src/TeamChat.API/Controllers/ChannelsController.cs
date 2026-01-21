using Microsoft.AspNetCore.Mvc;
using TeamChat.Core.DTOs;
using TeamChat.Core.Entities;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ChannelsController : ControllerBase
{
    private readonly IChannelRepository _channelRepository;
    private readonly IUserRepository _userRepository;
    private readonly ILogger<ChannelsController> _logger;

    public ChannelsController(
        IChannelRepository channelRepository,
        IUserRepository userRepository,
        ILogger<ChannelsController> logger)
    {
        _channelRepository = channelRepository;
        _userRepository = userRepository;
        _logger = logger;
    }

    /// <summary>
    /// Get all public channels.
    /// </summary>
    [HttpGet]
    public async Task<ActionResult<IEnumerable<ChannelDto>>> GetChannels()
    {
        var channels = await _channelRepository.GetPublicChannelsAsync();

        var dtos = channels.Select(c => new ChannelDto(
            Id: c.Id,
            Name: c.Name,
            Description: c.Description,
            Type: c.Type,
            IconUrl: c.IconUrl,
            MemberCount: c.MemberIds.Count,
            LastMessageAt: c.LastMessageAt
        ));

        return Ok(dtos);
    }

    /// <summary>
    /// Get channels for a specific user.
    /// </summary>
    [HttpGet("user/{userId}")]
    public async Task<ActionResult<IEnumerable<ChannelDto>>> GetUserChannels(string userId)
    {
        var channels = await _channelRepository.GetByUserIdAsync(userId);

        var dtos = channels.Select(c => new ChannelDto(
            Id: c.Id,
            Name: c.Name,
            Description: c.Description,
            Type: c.Type,
            IconUrl: c.IconUrl,
            MemberCount: c.MemberIds.Count,
            LastMessageAt: c.LastMessageAt
        ));

        return Ok(dtos);
    }

    /// <summary>
    /// Get a specific channel by ID.
    /// </summary>
    [HttpGet("{id}")]
    public async Task<ActionResult<ChannelDto>> GetChannel(string id)
    {
        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound();
        }

        var dto = new ChannelDto(
            Id: channel.Id,
            Name: channel.Name,
            Description: channel.Description,
            Type: channel.Type,
            IconUrl: channel.IconUrl,
            MemberCount: channel.MemberIds.Count,
            LastMessageAt: channel.LastMessageAt
        );

        return Ok(dto);
    }

    /// <summary>
    /// Create a new channel.
    /// </summary>
    [HttpPost]
    public async Task<ActionResult<ChannelDto>> CreateChannel([FromBody] CreateChannelDto dto, [FromQuery] string userId)
    {
        // Validate input
        if (string.IsNullOrWhiteSpace(dto.Name))
        {
            return BadRequest(new { error = "Channel name is required" });
        }

        if (dto.Name.Length < 2 || dto.Name.Length > 50)
        {
            return BadRequest(new { error = "Channel name must be between 2 and 50 characters" });
        }

        if (string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { error = "User ID is required" });
        }

        // Sanitize input - remove potentially dangerous characters
        var sanitizedName = dto.Name.Trim();
        var sanitizedDescription = dto.Description?.Trim();

        var channel = new Channel
        {
            Name = sanitizedName,
            Description = sanitizedDescription,
            Type = dto.Type,
            CreatedById = userId,
            MemberIds = new List<string> { userId }
        };

        channel = await _channelRepository.CreateAsync(channel);

        // Add channel to user
        await _userRepository.AddToChannelAsync(userId, channel.Id);

        _logger.LogInformation("Channel {ChannelId} created by user {UserId}", channel.Id, userId);

        var result = new ChannelDto(
            Id: channel.Id,
            Name: channel.Name,
            Description: channel.Description,
            Type: channel.Type,
            IconUrl: channel.IconUrl,
            MemberCount: channel.MemberIds.Count,
            LastMessageAt: channel.LastMessageAt
        );

        return CreatedAtAction(nameof(GetChannel), new { id = channel.Id }, result);
    }

    /// <summary>
    /// Update a channel.
    /// </summary>
    [HttpPut("{id}")]
    public async Task<ActionResult> UpdateChannel(string id, [FromBody] UpdateChannelDto dto)
    {
        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound();
        }

        if (!string.IsNullOrEmpty(dto.Name))
            channel.Name = dto.Name;
        if (dto.Description != null)
            channel.Description = dto.Description;
        if (dto.IconUrl != null)
            channel.IconUrl = dto.IconUrl;

        await _channelRepository.UpdateAsync(channel);

        return NoContent();
    }

    /// <summary>
    /// Delete a channel.
    /// </summary>
    [HttpDelete("{id}")]
    public async Task<ActionResult> DeleteChannel(string id)
    {
        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound();
        }

        await _channelRepository.DeleteAsync(id);

        _logger.LogInformation("Channel {ChannelId} deleted", id);

        return NoContent();
    }

    /// <summary>
    /// Join a channel.
    /// </summary>
    [HttpPost("{id}/join")]
    public async Task<ActionResult> JoinChannel(string id, [FromQuery] string userId)
    {
        if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { error = "Channel ID and User ID are required" });
        }

        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound(new { error = "Channel not found" });
        }

        // Check if user is already a member
        if (channel.MemberIds.Contains(userId))
        {
            return Ok(); // Already a member, no-op
        }

        await _channelRepository.AddMemberAsync(id, userId);
        await _userRepository.AddToChannelAsync(userId, id);

        _logger.LogInformation("User {UserId} joined channel {ChannelId}", userId, id);

        return Ok();
    }

    /// <summary>
    /// Leave a channel.
    /// </summary>
    [HttpPost("{id}/leave")]
    public async Task<ActionResult> LeaveChannel(string id, [FromQuery] string userId)
    {
        if (string.IsNullOrWhiteSpace(id) || string.IsNullOrWhiteSpace(userId))
        {
            return BadRequest(new { error = "Channel ID and User ID are required" });
        }

        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound(new { error = "Channel not found" });
        }

        await _channelRepository.RemoveMemberAsync(id, userId);
        await _userRepository.RemoveFromChannelAsync(userId, id);

        _logger.LogInformation("User {UserId} left channel {ChannelId}", userId, id);

        return Ok();
    }

    /// <summary>
    /// Get members of a channel.
    /// </summary>
    [HttpGet("{id}/members")]
    public async Task<ActionResult<IEnumerable<ChannelMemberDto>>> GetChannelMembers(string id)
    {
        var channel = await _channelRepository.GetByIdAsync(id);

        if (channel == null)
        {
            return NotFound();
        }

        var users = await _userRepository.GetByIdsAsync(channel.MemberIds);

        var dtos = users.Select(u => new ChannelMemberDto(
            UserId: u.Id,
            Username: u.Username,
            DisplayName: u.DisplayName,
            Status: u.Status
        ));

        return Ok(dtos);
    }
}
