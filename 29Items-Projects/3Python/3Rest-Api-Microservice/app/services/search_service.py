"""
Search Service - Full-text and Advanced Search
"""
from typing import Dict, Any, List, Optional
from datetime import datetime
from sqlalchemy import or_, and_, desc, asc
from app.models.customer import Customer
from app.extensions import db


class SearchService:
    """
    Service class for customer search functionality.
    """

    def search(
        self,
        query: str,
        field: str = 'all',
        page: int = 1,
        per_page: int = 20
    ) -> Dict[str, Any]:
        """
        Simple search across customer fields.

        Args:
            query: Search string
            field: Field to search ('all', 'name', 'email', 'company')
            page: Page number
            per_page: Items per page

        Returns:
            Search results with pagination
        """
        search_pattern = f'%{query}%'

        if field == 'all':
            filter_clause = or_(
                Customer.name.ilike(search_pattern),
                Customer.email.ilike(search_pattern),
                Customer.company.ilike(search_pattern),
                Customer.notes.ilike(search_pattern)
            )
        elif field == 'name':
            filter_clause = Customer.name.ilike(search_pattern)
        elif field == 'email':
            filter_clause = Customer.email.ilike(search_pattern)
        elif field == 'company':
            filter_clause = Customer.company.ilike(search_pattern)
        else:
            filter_clause = or_(
                Customer.name.ilike(search_pattern),
                Customer.email.ilike(search_pattern)
            )

        db_query = Customer.query.filter(filter_clause)
        pagination = db_query.paginate(page=page, per_page=per_page, error_out=False)

        return {
            'items': pagination.items,
            'page': page,
            'per_page': per_page,
            'total': pagination.total,
            'pages': pagination.pages
        }

    def advanced_search(
        self,
        filters: Dict[str, Any],
        sort_by: str = 'created_at',
        sort_order: str = 'desc',
        page: int = 1,
        per_page: int = 20
    ) -> Dict[str, Any]:
        """
        Advanced search with multiple filters.

        Args:
            filters: Dictionary of filter conditions
            sort_by: Field to sort by
            sort_order: 'asc' or 'desc'
            page: Page number
            per_page: Items per page

        Returns:
            Search results with pagination
        """
        query = Customer.query
        conditions = []

        # Apply filters
        if filters.get('name'):
            conditions.append(Customer.name.ilike(f'%{filters["name"]}%'))

        if filters.get('email'):
            conditions.append(Customer.email.ilike(f'%{filters["email"]}%'))

        if filters.get('company'):
            conditions.append(Customer.company.ilike(f'%{filters["company"]}%'))

        if filters.get('status'):
            conditions.append(Customer.status == filters['status'])

        if filters.get('created_after'):
            date = self._parse_date(filters['created_after'])
            if date:
                conditions.append(Customer.created_at >= date)

        if filters.get('created_before'):
            date = self._parse_date(filters['created_before'])
            if date:
                conditions.append(Customer.created_at <= date)

        if filters.get('sentiment_min'):
            conditions.append(Customer.sentiment_score >= float(filters['sentiment_min']))

        if filters.get('sentiment_max'):
            conditions.append(Customer.sentiment_score <= float(filters['sentiment_max']))

        if conditions:
            query = query.filter(and_(*conditions))

        # Apply sorting
        sort_column = getattr(Customer, sort_by, Customer.created_at)
        if sort_order == 'desc':
            query = query.order_by(desc(sort_column))
        else:
            query = query.order_by(asc(sort_column))

        pagination = query.paginate(page=page, per_page=per_page, error_out=False)

        return {
            'items': pagination.items,
            'page': page,
            'per_page': per_page,
            'total': pagination.total,
            'pages': pagination.pages
        }

    def _parse_date(self, date_string: str) -> Optional[datetime]:
        """Parse date string to datetime object."""
        try:
            return datetime.fromisoformat(date_string)
        except (ValueError, TypeError):
            return None
