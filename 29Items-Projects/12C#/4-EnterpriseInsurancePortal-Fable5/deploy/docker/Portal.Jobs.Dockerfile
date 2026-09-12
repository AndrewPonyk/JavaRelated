# Build context: repository root
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Directory.Build.props ./
# Jobs compile gRPC client stubs from Rating.Grpc's proto, so they need all of src/.
COPY src/ ./src/
RUN dotnet publish src/Jobs/Portal.Jobs -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app
COPY --from=build /app .
USER app
EXPOSE 8080
ENTRYPOINT ["dotnet", "Portal.Jobs.dll"]
