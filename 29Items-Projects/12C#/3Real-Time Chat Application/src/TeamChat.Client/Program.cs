using Microsoft.AspNetCore.Components;
using Microsoft.AspNetCore.Components.Authorization;
using Microsoft.AspNetCore.Components.Web;
using Microsoft.AspNetCore.Components.WebAssembly.Hosting;
using TeamChat.Client;
using TeamChat.Client.Services;

var builder = WebAssemblyHostBuilder.CreateDefault(args);
builder.RootComponents.Add<App>("#app");
builder.RootComponents.Add<HeadOutlet>("head::after");

// Configure HttpClient for API calls
var apiBaseUrl = builder.Configuration["ApiBaseUrl"];
if (string.IsNullOrEmpty(apiBaseUrl))
{
    // Fallback to same origin (works with nginx proxy)
    var navigationManager = builder.Services.BuildServiceProvider().GetRequiredService<NavigationManager>();
    apiBaseUrl = navigationManager.BaseUri;
}
builder.Services.AddScoped(sp => new HttpClient { BaseAddress = new Uri(apiBaseUrl) });

// Register local storage service
builder.Services.AddScoped<ILocalStorageService, LocalStorageService>();

// Register authentication services
builder.Services.AddScoped<AuthService>();
builder.Services.AddScoped<AuthenticationStateProvider>(sp => sp.GetRequiredService<AuthService>());
builder.Services.AddAuthorizationCore();

// Register application services
builder.Services.AddScoped<ApiService>();
builder.Services.AddScoped<ChatHubService>();

await builder.Build().RunAsync();
