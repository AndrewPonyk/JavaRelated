"""
Flask Extensions Initialization

Extensions are initialized without app context here,
then bound to the app in the factory function.
"""
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from flasgger import Swagger

# Database ORM
db = SQLAlchemy()

# Database migrations
migrate = Migrate()

# Swagger/OpenAPI documentation
swagger = Swagger()
