from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8")

    app_name: str = "Static Analyzer"
    app_env: str = "development"
    api_host: str = "0.0.0.0"
    api_port: int = 8000
    database_url: str = "sqlite:///./static_analyzer.db"
    cors_origins: str = "http://localhost:5173"
    analyzer_binary: str = "./analyzer/libtooling/build/static-analyzer"
    rules_config: str = "./config/rules.example.yaml"
    migrations_path: str = "./migrations"
    log_level: str = "INFO"
    max_source_bytes: int = 1_000_000
    max_page_size: int = 100
    gzip_min_size: int = 1_000
    enforce_https: bool = False
    allowed_hosts: str = "*"

    @property
    def cors_origin_list(self) -> list[str]:
        return [origin.strip() for origin in self.cors_origins.split(",") if origin.strip()]

    @property
    def allowed_host_list(self) -> list[str]:
        return [host.strip() for host in self.allowed_hosts.split(",") if host.strip()]


settings = Settings()
