"""
Database Seeding Script

Run this script to populate the database with sample data:
    python scripts/seed_db.py
"""
import sys
import os

# Add parent directory to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app
from app.extensions import db
from app.models.customer import Customer

# Sample data
SAMPLE_CUSTOMERS = [
    {
        'name': 'John Smith',
        'email': 'john.smith@acme.com',
        'phone': '+1-555-0101',
        'company': 'Acme Corporation',
        'notes': 'Excellent customer, always pays on time. Very happy with our services.',
        'status': 'active',
        'sentiment_score': 0.85
    },
    {
        'name': 'Jane Doe',
        'email': 'jane.doe@techcorp.io',
        'phone': '+1-555-0102',
        'company': 'TechCorp Inc',
        'notes': 'New enterprise client. Interested in premium features.',
        'status': 'active',
        'sentiment_score': 0.72
    },
    {
        'name': 'Bob Johnson',
        'email': 'bob.j@startup.co',
        'phone': '+1-555-0103',
        'company': 'StartupXYZ',
        'notes': 'Had some issues with billing. Needs follow-up.',
        'status': 'active',
        'sentiment_score': 0.45
    },
    {
        'name': 'Alice Williams',
        'email': 'alice@freelancer.me',
        'phone': '+1-555-0104',
        'company': None,
        'notes': 'Freelance designer. Uses basic plan.',
        'status': 'active',
        'sentiment_score': 0.68
    },
    {
        'name': 'Charlie Brown',
        'email': 'charlie@oldclient.org',
        'phone': '+1-555-0105',
        'company': 'Legacy Systems LLC',
        'notes': 'Account inactive for 6 months. Consider re-engagement campaign.',
        'status': 'inactive',
        'sentiment_score': 0.35
    },
    {
        'name': 'Diana Prince',
        'email': 'diana@bigcorp.com',
        'phone': '+1-555-0106',
        'company': 'Big Corporation',
        'notes': 'VIP client. Requires dedicated support.',
        'status': 'active',
        'sentiment_score': 0.92
    },
    {
        'name': 'Edward Norton',
        'email': 'edward@prospect.net',
        'phone': '+1-555-0107',
        'company': 'ProspectCo',
        'notes': 'Met at trade show. Interested in demo.',
        'status': 'lead',
        'sentiment_score': None
    },
    {
        'name': 'Fiona Green',
        'email': 'fiona@newlead.com',
        'phone': '+1-555-0108',
        'company': 'GreenTech Solutions',
        'notes': 'Referred by John Smith. Schedule intro call.',
        'status': 'lead',
        'sentiment_score': None
    },
    {
        'name': 'George Wilson',
        'email': 'george@enterprise.io',
        'phone': '+1-555-0109',
        'company': 'Enterprise Solutions',
        'notes': 'Long-term customer. Recently upgraded to enterprise plan. Very satisfied!',
        'status': 'active',
        'sentiment_score': 0.88
    },
    {
        'name': 'Helen Troy',
        'email': 'helen@churned.co',
        'phone': '+1-555-0110',
        'company': 'Former Client Inc',
        'notes': 'Cancelled subscription due to budget cuts. Bad experience with support.',
        'status': 'inactive',
        'sentiment_score': 0.22
    }
]


def seed_database():
    """Seed the database with sample customers."""
    app = create_app('development')

    with app.app_context():
        # Create tables if they don't exist
        db.create_all()

        # Check if data already exists
        existing_count = Customer.query.count()
        if existing_count > 0:
            print(f'Database already has {existing_count} customers.')
            response = input('Do you want to add more sample data? (y/n): ')
            if response.lower() != 'y':
                print('Aborted.')
                return

        # Insert sample customers
        for customer_data in SAMPLE_CUSTOMERS:
            # Check if email already exists
            if Customer.find_by_email(customer_data['email']):
                print(f"Skipping {customer_data['email']} - already exists")
                continue

            customer = Customer(**customer_data)
            db.session.add(customer)
            print(f"Added: {customer_data['name']}")

        db.session.commit()
        print(f'\nSeeding complete! Total customers: {Customer.query.count()}')


if __name__ == '__main__':
    seed_database()
