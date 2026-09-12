# EF Core migration bundle — run as a k8s Job before each rollout (expand/contract;
# every migration must be compatible with the previous app version).
# Build context: repository root
FROM mcr.microsoft.com/dotnet/sdk:8.0 AS build
WORKDIR /src
COPY Directory.Build.props ./
COPY .config/ ./.config/
COPY src/ ./src/
RUN dotnet tool restore \
 && dotnet ef migrations bundle \
      --project src/Services/Policy.Infrastructure \
      --self-contained -r linux-x64 -o /out/efbundle

FROM mcr.microsoft.com/dotnet/runtime-deps:8.0 AS runtime
WORKDIR /app
COPY --from=build /out/efbundle .
USER app
# Connection string supplied at run time:
#   ./efbundle --connection "$POLICY_DB_CONNECTION"
ENTRYPOINT ["./efbundle"]
