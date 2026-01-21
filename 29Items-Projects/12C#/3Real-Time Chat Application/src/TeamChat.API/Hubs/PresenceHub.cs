using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.SignalR;
using TeamChat.Core.DTOs;
using TeamChat.Core.Enums;
using TeamChat.Core.Interfaces;

namespace TeamChat.API.Hubs;

/// <summary>
/// SignalR hub for user presence (online/offline status).
/// </summary>
[Authorize]
public class PresenceHub : Hub
{
    private readonly IPresenceService _presenceService;
    private readonly IUserRepository _userRepository;
    private readonly ILogger<PresenceHub> _logger;

    public PresenceHub(
        IPresenceService presenceService,
        IUserRepository userRepository,
        ILogger<PresenceHub> logger)
    {
        _presenceService = presenceService;
        _userRepository = userRepository;
        _logger = logger;
    }

    public override async Task OnConnectedAsync()
    {
        var userId = GetUserId();
        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetOnlineAsync(userId, Context.ConnectionId);

            // Notify all clients about user coming online
            await Clients.Others.SendAsync("UserOnline", userId);

            _logger.LogInformation("User {UserId} presence connected", userId);
        }

        await base.OnConnectedAsync();
    }

    public override async Task OnDisconnectedAsync(Exception? exception)
    {
        var userId = GetUserId();
        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetOfflineAsync(userId, Context.ConnectionId);

            // Check if user is completely offline (no other connections)
            var status = await _presenceService.GetStatusAsync(userId);
            if (status == UserStatus.Offline)
            {
                await Clients.Others.SendAsync("UserOffline", userId);
            }

            _logger.LogInformation("User {UserId} presence disconnected", userId);
        }

        await base.OnDisconnectedAsync(exception);
    }

    /// <summary>
    /// Get online users from a list of user IDs.
    /// </summary>
    public async Task<IEnumerable<string>> GetOnlineUsers(IEnumerable<string> userIds)
    {
        return await _presenceService.GetOnlineUsersAsync(userIds);
    }

    /// <summary>
    /// Update user's status (away, do not disturb, etc.)
    /// </summary>
    public async Task UpdateStatus(UserStatus status)
    {
        var userId = GetUserId();
        if (!string.IsNullOrEmpty(userId))
        {
            await _presenceService.SetStatusAsync(userId, status);

            var presence = new UserPresenceDto(userId, status, DateTime.UtcNow);
            await Clients.Others.SendAsync("UserStatusChanged", presence);
        }
    }

    /// <summary>
    /// Get the current status of a user.
    /// </summary>
    public async Task<UserStatus> GetUserStatus(string userId)
    {
        return await _presenceService.GetStatusAsync(userId);
    }

    private string? GetUserId()
    {
        // First try to get from JWT claims (authenticated user)
        var userId = Context.User?.FindFirst(ClaimTypes.NameIdentifier)?.Value;
        if (!string.IsNullOrEmpty(userId))
            return userId;

        // Fallback to query string for development/testing
        return Context.GetHttpContext()?.Request.Query["userId"].ToString();
    }
}
