"""
Migration Helper Script

Provides utilities for managing database migrations.

Usage:
    python scripts/run_migrations.py init      # Initialize migrations
    python scripts/run_migrations.py migrate   # Create a new migration
    python scripts/run_migrations.py upgrade   # Apply migrations
    python scripts/run_migrations.py downgrade # Revert last migration
    python scripts/run_migrations.py current   # Show current revision
    python scripts/run_migrations.py history   # Show migration history
"""
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from flask_migrate import init, migrate, upgrade, downgrade, current, history
from app import create_app
from app.extensions import db


def run_command(command: str, message: str = None):
    """
    Run a migration command within app context.

    Args:
        command: The migration command to run
        message: Optional message for migrate command
    """
    config_name = os.environ.get('FLASK_ENV', 'development')
    app = create_app(config_name)

    with app.app_context():
        if command == 'init':
            print('Initializing migrations directory...')
            init()
            print('Done! Migrations directory created.')

        elif command == 'migrate':
            msg = message or 'Auto-generated migration'
            print(f'Creating migration: {msg}')
            migrate(message=msg)
            print('Done! New migration created.')

        elif command == 'upgrade':
            print('Applying migrations...')
            upgrade()
            print('Done! Database is up to date.')

        elif command == 'downgrade':
            print('Reverting last migration...')
            downgrade()
            print('Done! Migration reverted.')

        elif command == 'current':
            print('Current revision:')
            current()

        elif command == 'history':
            print('Migration history:')
            history()

        elif command == 'create-all':
            print('Creating all tables directly (without migrations)...')
            db.create_all()
            print('Done! All tables created.')

        elif command == 'drop-all':
            confirm = input('This will DROP ALL TABLES. Are you sure? (yes/no): ')
            if confirm.lower() == 'yes':
                db.drop_all()
                print('Done! All tables dropped.')
            else:
                print('Aborted.')

        else:
            print(f'Unknown command: {command}')
            print_help()
            sys.exit(1)


def print_help():
    """Print help message."""
    print(__doc__)


if __name__ == '__main__':
    if len(sys.argv) < 2:
        print_help()
        sys.exit(1)

    command = sys.argv[1]
    message = sys.argv[2] if len(sys.argv) > 2 else None

    if command in ['--help', '-h', 'help']:
        print_help()
    else:
        run_command(command, message)
