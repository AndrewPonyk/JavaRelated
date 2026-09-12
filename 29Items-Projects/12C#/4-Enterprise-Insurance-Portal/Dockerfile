# Multi-stage Dockerfile for .NET 9 Web API
# Stage 1: Build
FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy csproj and restore as distinct layers
COPY ["src/backend/EnterpriseInsurance.Api/EnterpriseInsurance.Api.csproj", "EnterpriseInsurance.Api/"]
COPY ["src/backend/EnterpriseInsurance.Core/EnterpriseInsurance.Core.csproj", "EnterpriseInsurance.Core/"]
COPY ["src/backend/EnterpriseInsurance.Infrastructure/EnterpriseInsurance.Infrastructure.csproj", "EnterpriseInsurance.Infrastructure/"]
RUN dotnet restore "EnterpriseInsurance.Api/EnterpriseInsurance.Api.csproj"

# Copy the rest of the code and build
COPY src/backend/ .
WORKDIR "/src/EnterpriseInsurance.Api"
RUN dotnet build "EnterpriseInsurance.Api.csproj" -c Release -o /app/build

# Stage 2: Publish
FROM build AS publish
RUN dotnet publish "EnterpriseInsurance.Api.csproj" -c Release -o /app/publish

# Stage 3: Run
FROM mcr.microsoft.com/dotnet/aspnet:9.0 AS final
WORKDIR /app
EXPOSE 8080
COPY --from=publish /app/publish .
ENTRYPOINT ["dotnet", "EnterpriseInsurance.Api.dll"]
