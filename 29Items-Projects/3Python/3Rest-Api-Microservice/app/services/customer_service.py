"""
Customer Service - Business Logic Layer

|su:32) SERVICE LAYER PATTERN - Services contain business logic, separate from API routes
        and models. This keeps code organized and testable.
"""
from typing import Optional, Dict, Any, List
from app.models.customer import Customer
from app.extensions import db
from app.utils.exceptions import NotFoundError, ValidationError


class CustomerService:
    """
    Service class for customer-related business logic.
    """

    # |su:33) PAGINATION PATTERN - Never return all records! Use page/per_page for large datasets
    def get_all(
        self,
        page: int = 1,
        per_page: int = 20,
        status: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Get all customers with pagination.

        Args:
            page: Page number (1-indexed)
            per_page: Items per page
            status: Filter by status

        Returns:
            Dictionary with items and pagination info
        """
        query = Customer.query

        # |su:34) QUERY BUILDING - Chain filters and ordering before executing
        if status:
            query = query.filter(Customer.status == status)

        query = query.order_by(Customer.created_at.desc())
        # |su:35) FLASK-SQLALCHEMY PAGINATION - Built-in pagination with total count
        pagination = query.paginate(page=page, per_page=per_page, error_out=False)

        return {
            'items': pagination.items,
            'page': page,
            'per_page': per_page,
            'total': pagination.total,
            'pages': pagination.pages
        }

    def get_by_id(self, customer_id: int) -> Optional[Customer]:
        """
        Get customer by ID.

        Args:
            customer_id: Customer ID

        Returns:
            Customer object or None
        """
        return Customer.query.get(customer_id)

    def create(self, data: Dict[str, Any]) -> Customer:
        """
        Create a new customer.

        Args:
            data: Customer data dictionary

        Returns:
            Created customer object

        Raises:
            ValidationError: If email already exists
        """
        # Check for existing email
        if Customer.find_by_email(data.get('email')):
            raise ValidationError('Email already exists')

        customer = Customer(
            name=data['name'],
            email=data['email'],
            phone=data.get('phone'),
            company=data.get('company'),
            notes=data.get('notes'),
            status=data.get('status', 'lead')
        )

        db.session.add(customer)
        db.session.commit()
        return customer

    def update(self, customer_id: int, data: Dict[str, Any]) -> Optional[Customer]:
        """
        Update an existing customer.

        Args:
            customer_id: Customer ID
            data: Updated data dictionary

        Returns:
            Updated customer object or None
        """
        customer = self.get_by_id(customer_id)
        if not customer:
            return None

        # Check email uniqueness if changing
        if 'email' in data and data['email'] != customer.email:
            existing = Customer.find_by_email(data['email'])
            if existing:
                raise ValidationError('Email already exists')

        # Update fields
        for field in ['name', 'email', 'phone', 'company', 'notes', 'status']:
            if field in data:
                setattr(customer, field, data[field])

        db.session.commit()
        return customer

    def delete(self, customer_id: int) -> bool:
        """
        Delete a customer.

        Args:
            customer_id: Customer ID

        Returns:
            True if deleted, False if not found
        """
        customer = self.get_by_id(customer_id)
        if not customer:
            return False

        db.session.delete(customer)
        db.session.commit()
        return True

    def update_sentiment(self, customer_id: int, sentiment_score: float) -> Optional[Customer]:
        """
        Update customer sentiment score.

        Args:
            customer_id: Customer ID
            sentiment_score: Sentiment score from NLP analysis

        Returns:
            Updated customer or None
        """
        customer = self.get_by_id(customer_id)
        if not customer:
            return None

        customer.sentiment_score = sentiment_score
        db.session.commit()
        return customer
