"""
Flask Extensions Initialization

|su:14) EXTENSIONS PATTERN - Create extension instances WITHOUT app, then init with app later.
        This supports the application factory pattern for testing with different configs.

Extensions are initialized without app context here,
then bound to the app in the factory function.
"""
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flasgger import Swagger

# |su:15) SQLALCHEMY ORM - Object-Relational Mapper for database operations without raw SQL
db = SQLAlchemy()

# |su:16) FLASK-MIGRATE - Alembic integration for database schema migrations (flask db migrate/upgrade)
migrate = Migrate()

# |su:17) SWAGGER/OPENAPI - Auto-generates API documentation at /apidocs from docstrings
swagger = Swagger()
