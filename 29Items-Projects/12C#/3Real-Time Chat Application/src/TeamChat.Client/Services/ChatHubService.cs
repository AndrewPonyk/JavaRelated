using Microsoft.AspNetCore.Components;
using Microsoft.AspNetCore.SignalR.Client;
using TeamChat.Core.DTOs;
using TeamChat.Core.Enums;

namespace TeamChat.Client.Services;

/// <summary>
/// Service for managing SignalR hub connections.
/// </summary>
public class ChatHubService : IAsyncDisposable
{
    private HubConnection? _chatConnection;
    private HubConnection? _presenceConnection;
    private readonly string _hubBaseUrl;

    public event Action<MessageDto>? OnMessageReceived;
    public event Action<string, string, DateTime>? OnMessageEdited;
    public event Action<string>? OnMessageDeleted;
    public event Action<string, ReactionType, string>? OnReactionAdded;
    public event Action<string, ReactionType, string>? OnReactionRemoved;
    public event Action<string, string, bool>? OnUserTyping;
    public event Action<string>? OnUserOnline;
    public event Action<string>? OnUserOffline;
    public event Action<UserPresenceDto>? OnUserStatusChanged;
    public event Action<Exception?>? OnConnectionClosed;
    public event Action<string>? OnConnectionStateChanged;

    public bool IsConnected => _chatConnection?.State == HubConnectionState.Connected;
    public HubConnectionState ConnectionState => _chatConnection?.State ?? HubConnectionState.Disconnected;

    public ChatHubService(IConfiguration configuration, NavigationManager navigationManager)
    {
        var configuredUrl = configuration["ApiBaseUrl"];

        // If the configured URL is relative or not set, use the current browser URL as base
        if (string.IsNullOrEmpty(configuredUrl) || configuredUrl.StartsWith("/"))
        {
            _hubBaseUrl = navigationManager.BaseUri.TrimEnd('/');
        }
        else
        {
            _hubBaseUrl = configuredUrl.TrimEnd('/');
        }
    }

    /// <summary>
    /// Connect to the chat and presence hubs with authentication token.
    /// </summary>
    public async Task ConnectAsync(string userId, string userName, string? accessToken = null)
    {
        await ConnectChatHubAsync(userId, userName, accessToken);
        await ConnectPresenceHubAsync(userId, accessToken);
    }

    private async Task ConnectChatHubAsync(string userId, string userName, string? accessToken)
    {
        var hubUrl = $"{_hubBaseUrl}/hubs/chat";

        // Add query parameters for fallback (when JWT isn't configured)
        if (string.IsNullOrEmpty(accessToken))
        {
            hubUrl += $"?userId={Uri.EscapeDataString(userId)}&userName={Uri.EscapeDataString(userName)}";
        }

        var builder = new HubConnectionBuilder()
            .WithUrl(hubUrl, options =>
            {
                if (!string.IsNullOrEmpty(accessToken))
                {
                    options.AccessTokenProvider = () => Task.FromResult<string?>(accessToken);
                }
            })
            .WithAutomaticReconnect(new[] {
                TimeSpan.Zero,
                TimeSpan.FromSeconds(2),
                TimeSpan.FromSeconds(5),
                TimeSpan.FromSeconds(10),
                TimeSpan.FromSeconds(30)
            });

        _chatConnection = builder.Build();

        // Register event handlers
        _chatConnection.On<MessageDto>("ReceiveMessage", message =>
        {
            OnMessageReceived?.Invoke(message);
        });

        _chatConnection.On<string, string, DateTime>("MessageEdited", (messageId, newContent, editedAt) =>
        {
            OnMessageEdited?.Invoke(messageId, newContent, editedAt);
        });

        _chatConnection.On<string>("MessageDeleted", messageId =>
        {
            OnMessageDeleted?.Invoke(messageId);
        });

        _chatConnection.On<string, ReactionType, string>("ReactionAdded", (messageId, reactionType, reactUserId) =>
        {
            OnReactionAdded?.Invoke(messageId, reactionType, reactUserId);
        });

        _chatConnection.On<string, ReactionType, string>("ReactionRemoved", (messageId, reactionType, reactUserId) =>
        {
            OnReactionRemoved?.Invoke(messageId, reactionType, reactUserId);
        });

        _chatConnection.On<string, string, bool>("UserTyping", (typingUserId, typingUserName, isTyping) =>
        {
            OnUserTyping?.Invoke(typingUserId, typingUserName, isTyping);
        });

        _chatConnection.Closed += error =>
        {
            OnConnectionClosed?.Invoke(error);
            return Task.CompletedTask;
        };

        _chatConnection.Reconnected += connectionId =>
        {
            OnConnectionStateChanged?.Invoke("Connected");
            return Task.CompletedTask;
        };

        _chatConnection.Reconnecting += error =>
        {
            OnConnectionStateChanged?.Invoke("Reconnecting");
            return Task.CompletedTask;
        };

        try
        {
            await _chatConnection.StartAsync();
            OnConnectionStateChanged?.Invoke("Connected");
        }
        catch (Exception)
        {
            OnConnectionStateChanged?.Invoke("Disconnected");
            throw;
        }
    }

    private async Task ConnectPresenceHubAsync(string userId, string? accessToken)
    {
        var hubUrl = $"{_hubBaseUrl}/hubs/presence";

        if (string.IsNullOrEmpty(accessToken))
        {
            hubUrl += $"?userId={Uri.EscapeDataString(userId)}";
        }

        var builder = new HubConnectionBuilder()
            .WithUrl(hubUrl, options =>
            {
                if (!string.IsNullOrEmpty(accessToken))
                {
                    options.AccessTokenProvider = () => Task.FromResult<string?>(accessToken);
                }
            })
            .WithAutomaticReconnect();

        _presenceConnection = builder.Build();

        _presenceConnection.On<string>("UserOnline", onlineUserId =>
        {
            OnUserOnline?.Invoke(onlineUserId);
        });

        _presenceConnection.On<string>("UserOffline", offlineUserId =>
        {
            OnUserOffline?.Invoke(offlineUserId);
        });

        _presenceConnection.On<UserPresenceDto>("UserStatusChanged", presence =>
        {
            OnUserStatusChanged?.Invoke(presence);
        });

        try
        {
            await _presenceConnection.StartAsync();
        }
        catch (Exception)
        {
            // Presence hub is optional - don't throw if connection fails
        }
    }

    /// <summary>
    /// Join a channel to receive messages.
    /// </summary>
    public async Task JoinChannelAsync(string channelId)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("JoinChannel", channelId);
        }
    }

    /// <summary>
    /// Leave a channel.
    /// </summary>
    public async Task LeaveChannelAsync(string channelId)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("LeaveChannel", channelId);
        }
    }

    /// <summary>
    /// Send a message to a channel.
    /// </summary>
    public async Task SendMessageAsync(string channelId, string content, string? threadId = null)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("SendMessage", channelId, content, threadId);
        }
    }

    /// <summary>
    /// Edit a message.
    /// </summary>
    public async Task EditMessageAsync(string messageId, string newContent)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("EditMessage", messageId, newContent);
        }
    }

    /// <summary>
    /// Delete a message.
    /// </summary>
    public async Task DeleteMessageAsync(string messageId)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("DeleteMessage", messageId);
        }
    }

    /// <summary>
    /// Add a reaction to a message.
    /// </summary>
    public async Task AddReactionAsync(string messageId, ReactionType reactionType)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("AddReaction", messageId, reactionType);
        }
    }

    /// <summary>
    /// Remove a reaction from a message.
    /// </summary>
    public async Task RemoveReactionAsync(string messageId, ReactionType reactionType)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("RemoveReaction", messageId, reactionType);
        }
    }

    /// <summary>
    /// Signal that user is typing.
    /// </summary>
    public async Task StartTypingAsync(string channelId)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("StartTyping", channelId);
        }
    }

    /// <summary>
    /// Signal that user stopped typing.
    /// </summary>
    public async Task StopTypingAsync(string channelId)
    {
        if (_chatConnection?.State == HubConnectionState.Connected)
        {
            await _chatConnection.InvokeAsync("StopTyping", channelId);
        }
    }

    /// <summary>
    /// Update user status.
    /// </summary>
    public async Task UpdateStatusAsync(UserStatus status)
    {
        if (_presenceConnection?.State == HubConnectionState.Connected)
        {
            await _presenceConnection.InvokeAsync("UpdateStatus", status);
        }
    }

    /// <summary>
    /// Disconnect from all hubs.
    /// </summary>
    public async Task DisconnectAsync()
    {
        if (_chatConnection is not null)
        {
            await _chatConnection.StopAsync();
        }
        if (_presenceConnection is not null)
        {
            await _presenceConnection.StopAsync();
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_chatConnection is not null)
        {
            await _chatConnection.DisposeAsync();
        }
        if (_presenceConnection is not null)
        {
            await _presenceConnection.DisposeAsync();
        }
    }
}
