# Multi-stage build. Build context must be the backend root: `src/backend`.
#   docker build -f src/InvoiceFactoring.Api/Dockerfile -t invoice-factoring-api .

# ---- build ----
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src

# Restore first (layer-cached): copy solution-level props + every csproj before the code.
COPY Directory.Build.props Directory.Packages.props ./
COPY src/InvoiceFactoring.Domain/*.csproj          src/InvoiceFactoring.Domain/
COPY src/InvoiceFactoring.Application/*.csproj      src/InvoiceFactoring.Application/
COPY src/InvoiceFactoring.Infrastructure/*.csproj   src/InvoiceFactoring.Infrastructure/
COPY src/InvoiceFactoring.ML/*.csproj               src/InvoiceFactoring.ML/
COPY src/InvoiceFactoring.Api/*.csproj              src/InvoiceFactoring.Api/
RUN dotnet restore src/InvoiceFactoring.Api/InvoiceFactoring.Api.csproj

# Copy the rest and publish.
COPY . .
RUN dotnet publish src/InvoiceFactoring.Api/InvoiceFactoring.Api.csproj \
    -c Release -o /app/publish /p:UseAppHost=false

# ---- runtime ----
FROM mcr.microsoft.com/dotnet/aspnet:8.0 AS final
WORKDIR /app

# Run as a non-root user.
RUN adduser --disabled-password --gecos "" --uid 1001 appuser
USER appuser

COPY --from=build /app/publish .

ENV ASPNETCORE_HTTP_PORTS=8080
EXPOSE 8080

# AKS probes hit these (see Program.cs / k8s manifests).
ENTRYPOINT ["dotnet", "InvoiceFactoring.Api.dll"]
