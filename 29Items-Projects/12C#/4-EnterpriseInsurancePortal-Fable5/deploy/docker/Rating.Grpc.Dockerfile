# Build context: repository root
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Directory.Build.props ./
COPY src/Services/Rating.Grpc/ ./src/Services/Rating.Grpc/
RUN dotnet publish src/Services/Rating.Grpc -c Release -o /app

FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS runtime
WORKDIR /app
COPY --from=build /app .
USER app
EXPOSE 8080
# h2c (HTTP/2 without TLS) for in-cluster gRPC; TLS terminates at the mesh/ingress.
ENV Kestrel__EndpointDefaults__Protocols=Http2
ENTRYPOINT ["dotnet", "Rating.Grpc.dll"]
