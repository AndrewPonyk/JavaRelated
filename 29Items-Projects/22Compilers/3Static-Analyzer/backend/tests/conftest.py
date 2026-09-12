import os

os.environ["DATABASE_URL"] = "sqlite:///:memory:"
os.environ["RULES_CONFIG"] = "./config/rules.example.yaml"
os.environ["MIGRATIONS_PATH"] = "./migrations"
