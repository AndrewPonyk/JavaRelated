# Build context: repository root
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Directory.Build.props ./
# The API compiles gRPC client stubs from Rating.Grpc's proto, so it needs all of src/.
COPY src/ ./src/
RUN dotnet publish src/Services/Policy.Api -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app
COPY --from=build /app .
USER app
EXPOSE 8080
ENTRYPOINT ["dotnet", "Policy.Api.dll"]
