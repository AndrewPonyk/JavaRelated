using System.Net.Http.Json;
using TeamChat.Core.DTOs;

namespace TeamChat.Client.Services;

/// <summary>
/// Service for making API calls to the backend.
/// </summary>
public class ApiService
{
    private readonly HttpClient _http;

    public ApiService(HttpClient http)
    {
        _http = http;
    }

    // Channels
    public async Task<IEnumerable<ChannelDto>> GetChannelsAsync()
    {
        return await _http.GetFromJsonAsync<IEnumerable<ChannelDto>>("api/channels") ?? [];
    }

    public async Task<IEnumerable<ChannelDto>> GetUserChannelsAsync(string userId)
    {
        return await _http.GetFromJsonAsync<IEnumerable<ChannelDto>>($"api/channels/user/{userId}") ?? [];
    }

    public async Task<ChannelDto?> GetChannelAsync(string channelId)
    {
        return await _http.GetFromJsonAsync<ChannelDto>($"api/channels/{channelId}");
    }

    public async Task<ChannelDto> CreateChannelAsync(CreateChannelDto dto, string userId)
    {
        var response = await _http.PostAsJsonAsync($"api/channels?userId={userId}", dto);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<ChannelDto>()
            ?? throw new InvalidOperationException("Failed to create channel");
    }

    public async Task<ChannelDto> CreateChannelAsync(string name, string? description = null)
    {
        var dto = new CreateChannelDto(name, description, Core.Enums.ChannelType.Public);
        var response = await _http.PostAsJsonAsync("api/channels", dto);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<ChannelDto>()
            ?? throw new InvalidOperationException("Failed to create channel");
    }

    public async Task JoinChannelAsync(string channelId, string userId)
    {
        var response = await _http.PostAsync($"api/channels/{channelId}/join?userId={userId}", null);
        response.EnsureSuccessStatusCode();
    }

    public async Task LeaveChannelAsync(string channelId, string userId)
    {
        var response = await _http.PostAsync($"api/channels/{channelId}/leave?userId={userId}", null);
        response.EnsureSuccessStatusCode();
    }

    // Messages
    public async Task<IEnumerable<MessageDto>> GetChannelMessagesAsync(string channelId, int skip = 0, int take = 50)
    {
        return await _http.GetFromJsonAsync<IEnumerable<MessageDto>>(
            $"api/messages/channel/{channelId}?skip={skip}&take={take}") ?? [];
    }

    public async Task<IEnumerable<MessageDto>> GetThreadMessagesAsync(string threadId, int skip = 0, int take = 50)
    {
        return await _http.GetFromJsonAsync<IEnumerable<MessageDto>>(
            $"api/messages/thread/{threadId}?skip={skip}&take={take}") ?? [];
    }

    // Users
    public async Task<UserDto?> GetUserAsync(string userId)
    {
        return await _http.GetFromJsonAsync<UserDto>($"api/users/{userId}");
    }

    public async Task<UserDto?> CreateUserAsync(CreateUserDto dto)
    {
        var response = await _http.PostAsJsonAsync("api/users", dto);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<UserDto>();
    }

    public async Task<IEnumerable<UserDto>> GetUsersAsync(IEnumerable<string> userIds)
    {
        var response = await _http.PostAsJsonAsync("api/users/batch", userIds);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<IEnumerable<UserDto>>() ?? [];
    }

    // Files
    public async Task<UploadFileResultDto?> UploadFileAsync(Stream fileStream, string fileName, string contentType)
    {
        using var content = new MultipartFormDataContent();
        using var fileContent = new StreamContent(fileStream);
        fileContent.Headers.ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue(contentType);
        content.Add(fileContent, "file", fileName);

        var response = await _http.PostAsync("api/files/upload", content);
        response.EnsureSuccessStatusCode();
        return await response.Content.ReadFromJsonAsync<UploadFileResultDto>();
    }
}
