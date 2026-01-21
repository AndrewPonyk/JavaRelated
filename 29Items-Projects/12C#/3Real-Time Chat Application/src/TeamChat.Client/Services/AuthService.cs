using System.Net.Http.Json;
using System.Security.Claims;
using System.Text.Json;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.JSInterop;
using TeamChat.Core.DTOs;

namespace TeamChat.Client.Services;

/// <summary>
/// Authentication service for managing user login state.
/// </summary>
public class AuthService : AuthenticationStateProvider
{
    private readonly HttpClient _httpClient;
    private readonly ILocalStorageService _localStorage;
    private AuthResponseDto? _currentAuth;

    public event Action? OnAuthStateChanged;

    public AuthService(HttpClient httpClient, ILocalStorageService localStorage)
    {
        _httpClient = httpClient;
        _localStorage = localStorage;
    }

    public string? Token => _currentAuth?.Token;
    public string? UserId => _currentAuth?.UserId;
    public string? Username => _currentAuth?.Username;
    public string? DisplayName => _currentAuth?.DisplayName;
    public bool IsAuthenticated => _currentAuth != null && _currentAuth.ExpiresAt > DateTime.UtcNow;

    public override async Task<AuthenticationState> GetAuthenticationStateAsync()
    {
        var token = await _localStorage.GetItemAsync("authToken");

        if (string.IsNullOrEmpty(token))
        {
            return new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()));
        }

        // Try to restore auth state from storage
        var authJson = await _localStorage.GetItemAsync("authData");
        if (!string.IsNullOrEmpty(authJson))
        {
            try
            {
                _currentAuth = JsonSerializer.Deserialize<AuthResponseDto>(authJson);
                if (_currentAuth != null && _currentAuth.ExpiresAt > DateTime.UtcNow)
                {
                    var claims = new[]
                    {
                        new Claim(ClaimTypes.NameIdentifier, _currentAuth.UserId),
                        new Claim(ClaimTypes.Name, _currentAuth.Username),
                        new Claim("display_name", _currentAuth.DisplayName ?? _currentAuth.Username)
                    };
                    var identity = new ClaimsIdentity(claims, "jwt");
                    return new AuthenticationState(new ClaimsPrincipal(identity));
                }
            }
            catch
            {
                // Invalid stored data
            }
        }

        return new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()));
    }

    public async Task<(bool Success, string? Error)> LoginAsync(string username, string password)
    {
        try
        {
            var response = await _httpClient.PostAsJsonAsync("api/auth/login", new { username, password });

            if (!response.IsSuccessStatusCode)
            {
                var error = await response.Content.ReadFromJsonAsync<ErrorResponse>();
                return (false, error?.Error ?? "Login failed");
            }

            var authResponse = await response.Content.ReadFromJsonAsync<AuthResponseDto>();
            if (authResponse == null)
            {
                return (false, "Invalid response from server");
            }

            await SetAuthStateAsync(authResponse);
            return (true, null);
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task<(bool Success, string? Error)> RegisterAsync(string username, string email, string password, string? displayName)
    {
        try
        {
            var response = await _httpClient.PostAsJsonAsync("api/auth/register", new
            {
                username,
                email,
                password,
                displayName
            });

            if (!response.IsSuccessStatusCode)
            {
                var error = await response.Content.ReadFromJsonAsync<ErrorResponse>();
                return (false, error?.Error ?? "Registration failed");
            }

            var authResponse = await response.Content.ReadFromJsonAsync<AuthResponseDto>();
            if (authResponse == null)
            {
                return (false, "Invalid response from server");
            }

            await SetAuthStateAsync(authResponse);
            return (true, null);
        }
        catch (Exception ex)
        {
            return (false, ex.Message);
        }
    }

    public async Task LogoutAsync()
    {
        _currentAuth = null;
        await _localStorage.RemoveItemAsync("authToken");
        await _localStorage.RemoveItemAsync("authData");

        NotifyAuthenticationStateChanged(Task.FromResult(
            new AuthenticationState(new ClaimsPrincipal(new ClaimsIdentity()))));
        OnAuthStateChanged?.Invoke();
    }

    private async Task SetAuthStateAsync(AuthResponseDto auth)
    {
        _currentAuth = auth;
        await _localStorage.SetItemAsync("authToken", auth.Token);
        await _localStorage.SetItemAsync("authData", JsonSerializer.Serialize(auth));

        var claims = new[]
        {
            new Claim(ClaimTypes.NameIdentifier, auth.UserId),
            new Claim(ClaimTypes.Name, auth.Username),
            new Claim("display_name", auth.DisplayName ?? auth.Username)
        };
        var identity = new ClaimsIdentity(claims, "jwt");

        NotifyAuthenticationStateChanged(Task.FromResult(
            new AuthenticationState(new ClaimsPrincipal(identity))));
        OnAuthStateChanged?.Invoke();
    }

    private record ErrorResponse(string Error);
}

/// <summary>
/// Simple local storage service interface.
/// </summary>
public interface ILocalStorageService
{
    Task<string?> GetItemAsync(string key);
    Task SetItemAsync(string key, string value);
    Task RemoveItemAsync(string key);
}

/// <summary>
/// Local storage service implementation using JavaScript interop.
/// </summary>
public class LocalStorageService : ILocalStorageService
{
    private readonly IJSRuntime _jsRuntime;

    public LocalStorageService(IJSRuntime jsRuntime)
    {
        _jsRuntime = jsRuntime;
    }

    public async Task<string?> GetItemAsync(string key)
    {
        return await _jsRuntime.InvokeAsync<string?>("localStorage.getItem", key);
    }

    public async Task SetItemAsync(string key, string value)
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.setItem", key, value);
    }

    public async Task RemoveItemAsync(string key)
    {
        await _jsRuntime.InvokeVoidAsync("localStorage.removeItem", key);
    }
}
