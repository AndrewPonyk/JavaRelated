using Portal.Shared.Kafka;
using Portal.Web.Components;
using Portal.Web.Eventing;
using Portal.Web.Hubs;
using Portal.Web.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddRazorComponents()
    .AddInteractiveServerComponents();

// SignalR — Azure SignalR Service backplane in non-local environments so Blazor
// pods can scale/roll without dropping circuits (TECH-NOTES.md pitfall #1).
var signalR = builder.Services.AddSignalR();
var azureSignalR = builder.Configuration["Azure:SignalR:ConnectionString"];
if (!string.IsNullOrEmpty(azureSignalR))
{
    signalR.AddAzureSignalR(azureSignalR);
}

builder.Services.AddHttpClient<PolicyApiClient>(client =>
{
    client.BaseAddress = new Uri(builder.Configuration["PolicyApi:BaseUrl"]
        ?? throw new InvalidOperationException("PolicyApi:BaseUrl is required."));
});

// Kafka → SignalR bridge for live policy updates (no-ops when Kafka is unconfigured)
var kafkaOptions = builder.Configuration.GetSection(KafkaOptions.SectionName).Get<KafkaOptions>() ?? new KafkaOptions();
builder.Services.AddSingleton(kafkaOptions);
builder.Services.AddHostedService<PolicyEventsKafkaConsumer>();

builder.Services.AddHealthChecks();

var app = builder.Build();

if (!app.Environment.IsDevelopment())
{
    app.UseExceptionHandler("/", createScopeForErrors: true);
}

app.UseStaticFiles();
app.UseAntiforgery();

app.MapRazorComponents<App>()
    .AddInteractiveServerRenderMode();
app.MapHub<PolicyHub>("/hubs/policies");
app.MapHealthChecks("/healthz");

app.Run();
