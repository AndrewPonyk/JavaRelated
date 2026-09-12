using Rating.Grpc.Engine;
using Rating.Grpc.Services;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddGrpc();
builder.Services.AddGrpcHealthChecks();
builder.Services.AddSingleton<RatingEngine>();

var app = builder.Build();

app.MapGrpcService<RatingGrpcService>();
app.MapGrpcHealthChecksService();
app.MapGet("/", () => "Rating.Grpc — use a gRPC client. Contract: Protos/rating.proto");

app.Run();
