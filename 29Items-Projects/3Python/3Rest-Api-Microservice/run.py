"""
Application Entry Point

|su:1) ENTRY POINT - This is where the application starts. Flask apps need an 'app' object ++
       that WSGI servers (gunicorn, uwsgi) or the development server can run.

Run the Flask development server:
    python run.py

For production, use gunicorn:
    gunicorn --bind 0.0.0.0:5000 run:app
"""
import os
from app import create_app

# |su:2) ENVIRONMENT CONFIG - Read FLASK_ENV to determine which config to use (dev/test/prod)
config_name = os.environ.get('FLASK_ENV', 'development')

# |su:3) APP FACTORY CALL - Create the Flask app using the factory pattern (see app/__init__.py) ++
app = create_app(config_name)

if __name__ == '__main__':
    # Run development server
    app.run(
        host='0.0.0.0',
        port=int(os.environ.get('PORT', 5000)),
        debug=app.config.get('DEBUG', True)
    )
