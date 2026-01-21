FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build
WORKDIR /src

# Copy project files
COPY ["src/TeamChat.Client/TeamChat.Client.csproj", "TeamChat.Client/"]
COPY ["src/TeamChat.Core/TeamChat.Core.csproj", "TeamChat.Core/"]

# Restore dependencies
RUN dotnet restore "TeamChat.Client/TeamChat.Client.csproj"

# Copy source code
COPY src/ .

# Build and publish
WORKDIR "/src/TeamChat.Client"
RUN dotnet publish "TeamChat.Client.csproj" -c Release -o /app/publish

# Use nginx to serve static files
FROM nginx:alpine AS final
WORKDIR /usr/share/nginx/html

# Copy published Blazor app
COPY --from=build /app/publish/wwwroot .

# Copy custom nginx config
COPY src/TeamChat.Client/nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
